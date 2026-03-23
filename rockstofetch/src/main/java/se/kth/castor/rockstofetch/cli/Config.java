package se.kth.castor.rockstofetch.cli;

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
  Integer llmMaxRetry
) {

  public enum EqualityFunction {
    DEEP_REFLECTIVE,
    ASSERT_J_DEEP,
    JUNIT
  }

  public enum GenerationMode {
    RULE_ONLY,
    LLM_FIRST_STATIC
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

  public EqualityFunction usedEqualityOrDefault() {
    return usedEquality == null ? EqualityFunction.JUNIT : usedEquality;
  }
}
