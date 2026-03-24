package org.threeTesters.runtime2test.engine.llm;

public record HybridLlmTestRequest(
    String mode,
    String projectPath,
    String staticSnapshot,
    String runtimeFacts,
    String assertionStyle,
    int maxMethods,
    int maxFactsPerMethod,
    boolean includeRawEvents
) {
}
