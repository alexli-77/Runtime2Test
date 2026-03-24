package se.kth.castor.rockstofetch.llm;

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
