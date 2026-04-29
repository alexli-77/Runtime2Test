package org.threeTesters.runtime2test.engine.cli;

import static org.threeTesters.runtime2test.construction.util.Statistics.addStatDuration;

import org.threeTesters.runtime2test.engine.cli.Config.GenerationMode;
import org.threeTesters.runtime2test.engine.cli.Config.EqualityFunction;
import org.threeTesters.runtime2test.engine.generate.DataReader;
import org.threeTesters.runtime2test.engine.generate.DataReader.LoadedInvocation;
import org.threeTesters.runtime2test.engine.generate.EventSequence;
import org.threeTesters.runtime2test.engine.generate.GenerationContext;
import org.threeTesters.runtime2test.engine.generate.GenerationContext.AssertionType;
import org.threeTesters.runtime2test.engine.generate.GenerationException;
import org.threeTesters.runtime2test.engine.generate.GenerationException.Type;
import org.threeTesters.runtime2test.engine.generate.JunitTestClass;
import org.threeTesters.runtime2test.engine.generate.JunitTestMethodOutputOracle;
import org.threeTesters.runtime2test.engine.generate.JunitTestMethodParameterOracle;
import org.threeTesters.runtime2test.engine.generate.PostProcessor;
import org.threeTesters.runtime2test.engine.generate.RuntimeFactsBuilder;
import org.threeTesters.runtime2test.engine.generate.TestFilterer;
import org.threeTesters.runtime2test.engine.instrument.InstrumentationConfiguration;
import org.threeTesters.runtime2test.engine.llm.HybridLlmClient;
import org.threeTesters.runtime2test.engine.llm.LlmGeneratedFile;
import org.threeTesters.runtime2test.engine.llm.LlmTestResponse;
import org.threeTesters.runtime2test.engine.llm.StaticLlmClient;
import org.threeTesters.runtime2test.engine.serialization.Json;
import org.threeTesters.runtime2test.engine.serialization.RockySerializer;
import org.threeTesters.runtime2test.engine.staticdata.StaticDataCollector;
import org.threeTesters.runtime2test.engine.util.SpoonAccessor;
import org.threeTesters.runtime2test.engine.util.Spoons;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.threeTesters.runtime2test.construction.serialization.UnknownActionHandler;
import org.threeTesters.runtime2test.construction.util.Statistics;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.MavenLauncher.SOURCE_TYPE;
import spoon.processing.Processor;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultImportComparator;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.ForceImportProcessor;
import spoon.reflect.visitor.ImportCleaner;
import spoon.reflect.visitor.ImportConflictDetector;
import spoon.support.compiler.VirtualFile;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

public class Generation {

  private static final String ASSERTJ_HELPER_PATH = "org/threeTesters/runtime2test/AssertJEqualityHelper.java";
  private static final String ASSERTJ_HELPER_FQN = ASSERTJ_HELPER_PATH
      .replace(".java", "")
      .replace("/", ".");

  public static void generate(
      Path methodsJson, Path testBasePath, Config config,
      Statistics statistics
  ) throws IOException {
    InstrumentationConfiguration instrumentationConfiguration = Objects.requireNonNull(
        new Json().fromJson(Files.readString(methodsJson), InstrumentationConfiguration.class)
    );
    Path projectPath = instrumentationConfiguration.projectPath();
    Path dataPath = instrumentationConfiguration.dataPath();

    GenerationMode mode = config.generationModeOrDefault();
    if (mode == GenerationMode.LLM_FIRST_STATIC && config.llmEndpoint() != null
        && !config.llmEndpoint().isBlank()) {
      try {
        generateWithStaticLlm(config, projectPath, dataPath, testBasePath);
        return;
      } catch (RuntimeException | IOException | InterruptedException e) {
        System.out.println("LLM static generation failed, falling back to RULE_ONLY: " + e);
      }
    }

    if (mode == GenerationMode.HYBRID_DYNAMIC && config.llmEndpoint() != null
        && !config.llmEndpoint().isBlank()) {
      try {
        generateWithHybridLlm(config, projectPath, dataPath, testBasePath);
        return;
      } catch (RuntimeException | IOException | InterruptedException e) {
        System.out.println("LLM hybrid generation failed, falling back to RULE_ONLY: " + e);
      }
    }

    generateWithRecordedInvocations(
        testBasePath,
        config.usedEqualityOrDefault(),
        config.filterTests(),
        statistics,
        projectPath,
        dataPath
    );
  }

  private static void generateWithRecordedInvocations(
      Path testBasePath,
      EqualityFunction equality,
      boolean filterTests,
      Statistics statistics,
      Path projectPath,
      Path dataPath
  ) throws IOException {

    Instant invocationReadStart = Instant.now();
    List<LoadedInvocation> invocations = new DataReader()
        .loadInvocations(dataPath)
        .stream()
        .sorted(Comparator.comparing(it -> Spoons.testName(it.invocation().recordedMethod(), "")))
        .toList();
    addStatDuration(statistics, "invocationRead", invocationReadStart);
    Instant eventSequenceReadStart = Instant.now();
    EventSequence events = EventSequence.fromSequence(
        new DataReader().readEvents(dataPath),
        statistics
    );
    addStatDuration(statistics, "eventSequenceRead", eventSequenceReadStart);

    SpoonAccessor spoonAccessor = new SpoonAccessor(projectPath);

    RockySerializer noMockSerializer = new RockySerializer(
        spoonAccessor, Set.of(), Set.of(), Set.of(), UnknownActionHandler.fail(), null
    );

    BiFunction<AssertionType, CtTypeReference<?>, CtStatement> equalityFunction;
    if (equality == EqualityFunction.DEEP_REFLECTIVE) {
      equalityFunction = Spoons.getDeepReflectiveAssertFunction();
    } else if (equality == EqualityFunction.ASSERT_J_DEEP) {
      equalityFunction = Spoons.getDeepAssertJAssertFunction(
          ASSERTJ_HELPER_FQN,
          statistics != null
      );
    } else {
      equalityFunction = GenerationContext.defaultAssertFunction();
    }

    Map<JunitTestClass, List<LoadedInvocation>> loadedInvocations = invocations.stream()
        .collect(Collectors.groupingBy(
            loadedInvocation -> loadedInvocation.invocation().recordedMethod().declaringClassName())
        )
        .entrySet()
        .stream()
        .collect(Collectors.toMap(
            entry -> new JunitTestClass(
                spoonAccessor.getFactory(),
                entry.getKey().replace("$", "") + "RockyTest"
            ),
            Entry::getValue
        ));
    Instant generateTestsStart = Instant.now();
    List<Integer> objPerTest = new ArrayList<>();
    for (var entry : loadedInvocations.entrySet()) {
      for (LoadedInvocation loadedInvocation : entry.getValue()) {
        tryAddMethod(
            () -> {
              objPerTest.add(new JunitTestMethodOutputOracle(
                  spoonAccessor.getFactory(), loadedInvocation, events, equalityFunction
              ).buildTest(entry.getKey().getMethodCache(), statistics));
            }
        );

        tryAddMethod(
            () -> JunitTestMethodParameterOracle.forInvocation(
                spoonAccessor.getFactory(),
                noMockSerializer,
                loadedInvocation,
                events,
                statistics
            )
        );
      }
      entry.getKey().finalizeMethodCache();
    }
    loadedInvocations.keySet().removeIf(JunitTestClass::isEmpty);
    // Dependency hell, just not worth it.
    loadedInvocations.keySet()
        .removeIf(it -> it.getQualifiedName().endsWith("PDFToImageRockyTest"));

    addStatDuration(statistics, "generateTests", generateTestsStart);

    objPerTest.sort(Comparator.naturalOrder());
    if (objPerTest.isEmpty()) {
      System.out.println("No candidate tests were produced during generation.");
    } else {
      System.out.println(objPerTest);
      System.out.println("I got median of " + objPerTest.get(objPerTest.size() / 2));
    }

    for (JunitTestClass testClass : loadedInvocations.keySet()) {
      if (equality == EqualityFunction.DEEP_REFLECTIVE) {
        Spoons.getReflectiveDeepEqualsMethods(spoonAccessor.getFactory())
            .forEach(testClass::addMethod);
      }
    }

    Files.createDirectories(testBasePath);
    if (Files.exists(testBasePath)) {
      try (Stream<Path> paths = Files.walk(testBasePath)) {
        for (Path path : paths.filter(it -> it.toString().endsWith("RockyTest.java")).toList()) {
          Files.delete(path);
        }
      }
    }
    Files.deleteIfExists(testBasePath.resolve(ASSERTJ_HELPER_PATH));

    if (equality == EqualityFunction.ASSERT_J_DEEP) {
      Files.createDirectories(testBasePath.resolve(ASSERTJ_HELPER_PATH).getParent());
      Files.writeString(
          testBasePath.resolve(ASSERTJ_HELPER_PATH),
          "package " + ASSERTJ_HELPER_FQN.substring(0, ASSERTJ_HELPER_FQN.lastIndexOf('.')) + ";\n"
          + Spoons.getAssertJDeepEqualsClass()
      );
    }

    Instant postProcessStart = Instant.now();
    List<CtClass<?>> processed = postProcess(
        statistics, projectPath, loadedInvocations.keySet(), filterTests,
        testBasePath.resolve(ASSERTJ_HELPER_PATH)
    );
    addStatDuration(statistics, "postProcess", postProcessStart);

    Instant writeStart = Instant.now();
    for (CtClass<?> testClass : processed) {
      Path testPath = testBasePath.resolve(
          testClass.getTopLevelType().getQualifiedName().replace(".", "/") + ".java"
      );
      System.out.println("Writing " + testPath.toAbsolutePath().normalize());
      Files.createDirectories(testPath.getParent());
      Files.writeString(testPath, testClass.toStringWithImports());
    }
    addStatDuration(statistics, "write", writeStart);
  }

  private static void generateWithStaticLlm(
      Config config,
      Path projectPath,
      Path dataPath,
      Path testBasePath
  ) throws IOException, InterruptedException {
    Path staticSnapshot = dataPath.resolve(StaticDataCollector.STATIC_SNAPSHOT_FILE);
    if (!Files.exists(staticSnapshot)) {
      throw new IOException("Missing static snapshot: " + staticSnapshot.toAbsolutePath());
    }

    LlmTestResponse response = new StaticLlmClient().generateTests(
      parseLlmEndpoint(config.llmEndpoint()),
        projectPath,
        staticSnapshot,
        config.usedEqualityOrDefault().name(),
        config.llmTimeoutMsOrDefault(),
        config.llmMaxRetryOrDefault()
    );

    if (!response.success()) {
      throw new RuntimeException("LLM response unsuccessful: " + response.message());
    }

    clearGeneratedTests(testBasePath);
    writeGeneratedFiles(testBasePath, response.files());
  }

  private static void generateWithHybridLlm(
      Config config,
      Path projectPath,
      Path dataPath,
      Path testBasePath
  ) throws IOException, InterruptedException {
    Path staticSnapshot = dataPath.resolve(StaticDataCollector.STATIC_SNAPSHOT_FILE);
    if (!Files.exists(staticSnapshot)) {
      throw new IOException("Missing static snapshot: " + staticSnapshot.toAbsolutePath());
    }

    String runtimeFacts = "";
    if (config.hybridEnableRuntimeFactsOrDefault()) {
      runtimeFacts = new RuntimeFactsBuilder().buildFacts(
        dataPath,
        config.hybridMaxMethodsOrDefault(),
        config.hybridMaxFactsPerMethodOrDefault()
      );
    }

    LlmTestResponse response = new HybridLlmClient().generateTests(
      parseLlmEndpoint(config.llmEndpoint()),
        projectPath,
        staticSnapshot,
        runtimeFacts,
        config.usedEqualityOrDefault().name(),
        config.llmTimeoutMsOrDefault(),
        config.llmMaxRetryOrDefault(),
        config.hybridMaxMethodsOrDefault(),
        config.hybridMaxFactsPerMethodOrDefault(),
        config.hybridIncludeRawEventsOrDefault()
    );

    if (!response.success()) {
      throw new RuntimeException("LLM response unsuccessful: " + response.message());
    }

    clearGeneratedTests(testBasePath);
    writeGeneratedFiles(testBasePath, response.files());
  }

  private static void clearGeneratedTests(Path testBasePath) throws IOException {
    Files.createDirectories(testBasePath);
    if (Files.exists(testBasePath)) {
      try (Stream<Path> paths = Files.walk(testBasePath)) {
        for (Path path : paths.filter(it -> it.toString().endsWith("RockyTest.java")).toList()) {
          Files.delete(path);
        }
      }
    }
  }

  private static void writeGeneratedFiles(Path testBasePath, List<LlmGeneratedFile> files)
      throws IOException {
    for (LlmGeneratedFile file : files) {
      if (file.relativePath() == null || file.relativePath().isBlank()) {
        continue;
      }
      Path out = testBasePath.resolve(file.relativePath()).normalize();
      if (!out.startsWith(testBasePath.normalize())) {
        throw new IOException("Refusing to write outside test base path: " + file.relativePath());
      }
      Files.createDirectories(out.getParent());
      Files.writeString(out, file.content() == null ? "" : file.content());
      System.out.println("Writing " + out.toAbsolutePath());
    }
  }

  private static URI parseLlmEndpoint(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("llmEndpoint is blank");
    }

    String normalized = endpoint.trim();
    if (!normalized.contains("://")) {
      normalized = "https://" + normalized;
    }
    return URI.create(normalized);
  }

  private static void tryAddMethod(Runnable creationAction) {
    try {
      creationAction.run();
    } catch (GenerationException e) {
      // TODO: Maybe try fixing some...
      if (e.getType() == Type.REFERENCED_OBJECT_NOT_FOUND) {
        System.out.println(e.getMessage());
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  private static List<CtClass<?>> postProcess(
      Statistics statistics, Path projectPath, Collection<JunitTestClass> testClasses,
      boolean filterTests, Path... extraFiles
  ) {
    List<CtClass<?>> tests = buildModel(
        testClasses.stream()
            .collect(Collectors.toMap(JunitTestClass::getQualifiedName, JunitTestClass::serialize)),
        projectPath,
        (launcher, ctClass) -> new PostProcessor(statistics).process(ctClass),
        extraFiles
    );
    if (filterTests) {
      tests = buildModel(
          tests.stream()
              .collect(Collectors.toMap(CtType::getQualifiedName, CtType::toStringWithImports)),
          projectPath,
          (launcher, ctClass) -> new TestFilterer().filter(
              ctClass,
              ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()
          ),
          extraFiles
      );
    }

    return tests;
  }

  private static List<CtClass<?>> buildModel(
      Map<String, String> testClasses,
      Path projectPath,
      BiConsumer<Launcher, CtClass<?>> hook,
      Path... extraFiles
  ) {
    Launcher launcher = new MavenLauncher(projectPath.toString(), SOURCE_TYPE.APP_SOURCE);
    Arrays.stream(extraFiles).map(Path::toString).forEach(launcher::addInputResource);
    launcher.getEnvironment().setComplianceLevel(11);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setSourceClasspath(
        withOwnClassPath(
            new MavenLauncher(
                projectPath.toString(), SOURCE_TYPE.ALL_SOURCE, Pattern.compile(".*")
            ).getEnvironment().getSourceClasspath()
        )
    );
    for (var entry : testClasses.entrySet()) {
      launcher.addInputResource(new VirtualFile(
          entry.getValue(),
          entry.getKey().replace(".", "/") + ".java"
      ));
    }
    launcher.getEnvironment()
        .setPrettyPrinterCreator(() -> new DefaultJavaPrettyPrinter(launcher.getEnvironment()) {
          {
            setMinimizeRoundBrackets(true);
            List<Processor<CtElement>> preprocessors = List.of(
                //try to import as much types as possible
                new ForceImportProcessor(),
                //remove unused imports first. Do not add new imports at time when conflicts are not resolved
                new ImportCleaner().setCanAddImports(false),
                //solve conflicts, the current imports are relevant too
                new ImportConflictDetector(),
                //compute final imports
                new ImportCleaner().setImportComparator(new DefaultImportComparator())
            );
            setIgnoreImplicit(false);
            setPreprocessors(preprocessors);
          }
        });
    launcher.buildModel();

    List<CtClass<?>> tests = new ArrayList<>();
    for (String testName : testClasses.keySet()) {
      CtClass<?> test = launcher.getFactory().Class().get(testName);
      hook.accept(launcher, test);

      tests.add(test);
    }

    return tests;
  }

  private static String[] withOwnClassPath(String[] existing) {
    String ourDirectory = ".";
    URL ourLocation = Generation.class.getProtectionDomain()
        .getCodeSource()
        .getLocation();
    if (ourLocation.toString().endsWith("target/classes/")) {
      ourDirectory = "runtime2test-engine";
    }
    List<String> ours = new ArrayList<>(Arrays.asList(
        new MavenLauncher(ourDirectory, SOURCE_TYPE.ALL_SOURCE).getEnvironment()
            .getSourceClasspath()
    ));
    ours.addAll(Arrays.asList(existing));
    return ours.toArray(String[]::new);
  }
}
