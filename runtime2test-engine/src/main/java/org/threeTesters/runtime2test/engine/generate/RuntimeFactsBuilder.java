package org.threeTesters.runtime2test.engine.generate;

import org.threeTesters.runtime2test.engine.generate.DataReader.LoadedInvocation;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder.Event;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder.Event.CallMethodEndEvent;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder.Event.CallMethodStartEvent;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder.Event.CallMutatorEvent;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder.Event.ConstructEvent;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder.Event.SetFieldEvent;
import org.threeTesters.runtime2test.engine.serialization.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeFactsBuilder {

  private final Json json;

  public RuntimeFactsBuilder() {
    this.json = new Json();
  }

  public String buildFacts(Path dataPath, int maxMethods, int maxFactsPerMethod) throws IOException {
    DataReader reader = new DataReader();
    List<LoadedInvocation> invocations = reader.loadInvocations(dataPath);

    Map<String, MethodRuntimeFactsAccumulator> perMethod = new HashMap<>();
    for (LoadedInvocation loaded : invocations) {
      String methodId = loaded.invocation().recordedMethod().fqnWithSignature();
      MethodRuntimeFactsAccumulator facts = perMethod.computeIfAbsent(
          methodId,
          ignored -> new MethodRuntimeFactsAccumulator(methodId)
      );
      facts.accept(loaded, maxFactsPerMethod);
    }

    EventCounts eventCounts = collectEventCounts(reader, dataPath);

    List<MethodRuntimeFacts> methodFacts = perMethod.values().stream()
        .sorted(Comparator.comparingInt(MethodRuntimeFactsAccumulator::invocationCount).reversed())
        .limit(Math.max(1, maxMethods))
        .map(MethodRuntimeFactsAccumulator::toFacts)
        .toList();

    HybridRuntimeFacts payload = new HybridRuntimeFacts(
        invocations.size(),
        methodFacts,
        eventCounts
    );

    return json.toJson(payload);
  }

  private EventCounts collectEventCounts(DataReader reader, Path dataPath) throws IOException {
    int constructs = 0;
    int setFields = 0;
    int callStarts = 0;
    int callEnds = 0;
    int mutatorCalls = 0;

    try (var events = reader.readEvents(dataPath)) {
      for (Event event : (Iterable<Event>) events::iterator) {
        if (event instanceof ConstructEvent) {
          constructs++;
        } else if (event instanceof SetFieldEvent) {
          setFields++;
        } else if (event instanceof CallMethodStartEvent) {
          callStarts++;
        } else if (event instanceof CallMethodEndEvent) {
          callEnds++;
        } else if (event instanceof CallMutatorEvent) {
          mutatorCalls++;
        }
      }
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }

    return new EventCounts(constructs, setFields, callStarts, callEnds, mutatorCalls);
  }

  private static class MethodRuntimeFactsAccumulator {

    private final String methodId;
    private int invocationCount;
    private int voidReturns;
    private int valueReturns;
    private int nestedCalls;
    private int mockedCalls;
    private final List<String> parameterShapes;

    private MethodRuntimeFactsAccumulator(String methodId) {
      this.methodId = methodId;
      this.parameterShapes = new ArrayList<>();
    }

    private void accept(LoadedInvocation loaded, int maxFactsPerMethod) {
      invocationCount++;
      if (loaded.invocation().isVoid()) {
        voidReturns++;
      } else {
        valueReturns++;
      }
      nestedCalls += loaded.nestedInvocations().size();
      mockedCalls += loaded.mockedInvocations().size();

      if (parameterShapes.size() < Math.max(1, maxFactsPerMethod)) {
        String shape = loaded.invocation().parameters().stream()
            .map(it -> it.staticType() == null ? "unknown" : it.staticType())
            .reduce((a, b) -> a + "," + b)
            .orElse("");
        if (!parameterShapes.contains(shape)) {
          parameterShapes.add(shape);
        }
      }
    }

    private int invocationCount() {
      return invocationCount;
    }

    private MethodRuntimeFacts toFacts() {
      return new MethodRuntimeFacts(
          methodId,
          invocationCount,
          voidReturns,
          valueReturns,
          nestedCalls,
          mockedCalls,
          List.copyOf(parameterShapes)
      );
    }
  }

  public record HybridRuntimeFacts(
      int totalInvocations,
      List<MethodRuntimeFacts> methods,
      EventCounts eventCounts
  ) {
  }

  public record MethodRuntimeFacts(
      String methodId,
      int invocationCount,
      int voidReturns,
      int valueReturns,
      int nestedCalls,
      int mockedCalls,
      List<String> parameterShapes
  ) {
  }

  public record EventCounts(
      int constructs,
      int setFields,
      int callStarts,
      int callEnds,
      int mutatorCalls
  ) {
  }
}
