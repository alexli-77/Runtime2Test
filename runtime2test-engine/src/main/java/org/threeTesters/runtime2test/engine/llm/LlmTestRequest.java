package org.threeTesters.runtime2test.engine.llm;

public record LlmTestRequest(
    String mode,
    String projectPath,
    String staticSnapshot,
    String assertionStyle
) {
}
