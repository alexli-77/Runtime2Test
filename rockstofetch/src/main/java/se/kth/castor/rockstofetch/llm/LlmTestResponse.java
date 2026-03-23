package se.kth.castor.rockstofetch.llm;

import java.util.List;

public record LlmTestResponse(
    boolean success,
    String message,
    List<LlmGeneratedFile> files
) {

  public LlmTestResponse {
    files = files == null ? List.of() : List.copyOf(files);
  }
}
