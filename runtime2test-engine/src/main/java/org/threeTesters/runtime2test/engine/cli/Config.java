package org.threeTesters.runtime2test.engine.cli;

import java.nio.file.Path;
import java.util.Set;

public record Config(
    boolean ignoreCoverage,
    Path projectPath,
    Path methodsJson,
    Path dataPath,
    Path testBasePath,
    String productionCommand,
    Set<String> additionalInstrumentedPackages,
    EqualityFunction usedEquality,
  boolean filterTests,
  GenerationMode generationMode,
  String llmEndpoint,
  Integer llmTimeoutMs,
  Integer llmMaxRetry,
  Integer hybridMaxMethods,
  Integer hybridMaxFactsPerMethod,
  Boolean hybridIncludeRawEvents,
  Boolean hybridEnableRuntimeFacts
) {

  public enum EqualityFunction {
    DEEP_REFLECTIVE,
    ASSERT_J_DEEP,
    JUNIT
  }

  public enum GenerationMode {
    RULE_ONLY,
    LLM_FIRST_STATIC,
    HYBRID_DYNAMIC
  }

  public GenerationMode generationModeOrDefault() {
    return generationMode == null ? GenerationMode.RULE_ONLY : generationMode;
  }

  public int llmTimeoutMsOrDefault() {
    return llmTimeoutMs == null ? 45_000 : llmTimeoutMs;
  }

  public int llmMaxRetryOrDefault() {
    return llmMaxRetry == null ? 1 : llmMaxRetry;
  }

  public int hybridMaxMethodsOrDefault() {
    return hybridMaxMethods == null ? 100 : hybridMaxMethods;
  }

  public int hybridMaxFactsPerMethodOrDefault() {
    return hybridMaxFactsPerMethod == null ? 20 : hybridMaxFactsPerMethod;
  }

  public boolean hybridIncludeRawEventsOrDefault() {
    return hybridIncludeRawEvents != null && hybridIncludeRawEvents;
  }

  public boolean hybridEnableRuntimeFactsOrDefault() {
    return hybridEnableRuntimeFacts == null || hybridEnableRuntimeFacts;
  }

  public EqualityFunction usedEqualityOrDefault() {
    return usedEquality == null ? EqualityFunction.JUNIT : usedEquality;
  }
}
