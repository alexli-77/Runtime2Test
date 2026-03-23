package se.kth.castor.rockstofetch.llm;

public record LlmTestRequest(
    String mode,
    String projectPath,
    String staticSnapshot,
    String assertionStyle
) {
}
