package se.kth.castor.rockstofetch.staticdata;

import se.kth.castor.rockstofetch.extract.ExtractCandidateMethod;
import se.kth.castor.rockstofetch.generate.Callgraph;
import se.kth.castor.rockstofetch.instrument.RecordedMethod;
import se.kth.castor.rockstofetch.serialization.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public class StaticDataCollector {

  public static final String STATIC_SNAPSHOT_FILE = "static-snapshot.json";

  private final Json json;

  public StaticDataCollector() {
    this.json = new Json();
  }

  public Path writeSnapshot(
      Path dataPath,
      Path projectPath,
      List<ExtractCandidateMethod> candidates
  ) throws IOException {
    Map<String, Callgraph> callgraphsByType = buildCallgraphs(candidates);

    List<StaticMethodSnapshot> methods = candidates.stream()
        .map(candidate -> toMethodSnapshot(candidate, callgraphsByType))
        .sorted(Comparator.comparing(StaticMethodSnapshot::methodId))
        .toList();

    StaticProjectSnapshot snapshot = new StaticProjectSnapshot(
        1,
        Instant.now(),
        projectPath.toAbsolutePath().normalize(),
        methods.size(),
        methods
    );

    Path output = dataPath.resolve(STATIC_SNAPSHOT_FILE);
    Files.writeString(output, json.prettyPrint(snapshot));
    return output;
  }

  private static Map<String, Callgraph> buildCallgraphs(List<ExtractCandidateMethod> candidates) {
    Map<String, CtType<?>> types = new HashMap<>();
    for (ExtractCandidateMethod candidate : candidates) {
      CtMethod<?> method = candidate.spoonMethod();
      if (method.getDeclaringType() != null) {
        types.putIfAbsent(method.getDeclaringType().getQualifiedName(), method.getDeclaringType());
      }
    }

    return types.entrySet().stream()
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            it -> Callgraph.forType(it.getValue())
        ));
  }

  private static StaticMethodSnapshot toMethodSnapshot(
      ExtractCandidateMethod candidate,
      Map<String, Callgraph> callgraphsByType
  ) {
    CtMethod<?> method = candidate.spoonMethod();
    RecordedMethod recorded = candidate.recordingCandidate().toRecordedMethod();
    String declaringType = method.getDeclaringType().getQualifiedName();
    Callgraph callgraph = callgraphsByType.get(declaringType);

    List<String> callers = callgraph == null
        ? List.of()
        : callgraph.getDirectCallers(method).stream().map(StaticDataCollector::methodId).sorted().toList();
    List<String> callees = callgraph == null
        ? List.of()
        : callgraph.getDirectCallees(method).stream().map(StaticDataCollector::methodId).sorted().toList();

    List<String> nestedTargets = candidate.recordingCandidate().nestedInvocations().stream()
        .map(it -> it.toRecordedMethod().fqnWithSignature())
        .sorted()
        .toList();

    return new StaticMethodSnapshot(
        recorded.fqnWithSignature(),
        declaringType,
        method.getSimpleName(),
        method.getType().getTypeErasure().getQualifiedName(),
        method.getType().getTypeErasure().getQualifiedName().equals("void"),
        method.getParameters().stream().map(it -> it.getSimpleName()).toList(),
        method.getParameters().stream().map(it -> it.getType().getTypeErasure().getQualifiedName()).toList(),
        nestedTargets,
        callers,
        callees,
        callgraph != null && callgraph.calledByTest(method),
        method.toString()
    );
  }

  private static String methodId(CtMethod<?> method) {
    return method.getDeclaringType().getQualifiedName()
           + "#"
           + method.getSimpleName()
           + "("
           + method.getParameters().stream()
               .map(it -> it.getType().getTypeErasure().getQualifiedName())
               .collect(Collectors.joining(","))
           + ")";
  }
}
