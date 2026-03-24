package org.threeTesters.runtime2test.engine.llm;

public record LlmGeneratedFile(
    String relativePath,
    String content
) {
}
