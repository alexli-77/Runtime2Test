package se.kth.castor.rockstofetch.llm;

public record LlmGeneratedFile(
    String relativePath,
    String content
) {
}
