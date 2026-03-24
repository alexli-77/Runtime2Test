package org.threeTesters.runtime2test.engine.llm;

import org.threeTesters.runtime2test.engine.serialization.Json;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

public class StaticLlmClient {

  private final HttpClient httpClient;
  private final Json json;

  public StaticLlmClient() {
    this.httpClient = HttpClient.newHttpClient();
    this.json = new Json();
  }

  public LlmTestResponse generateTests(
      URI endpoint,
      Path projectPath,
      Path staticSnapshotPath,
      String assertionStyle,
      int timeoutMs,
      int maxRetry
  ) throws IOException, InterruptedException {
    Objects.requireNonNull(endpoint);
    String snapshot = Files.readString(staticSnapshotPath);
    LlmTestRequest payload = new LlmTestRequest(
        "LLM_FIRST_STATIC",
        projectPath.toAbsolutePath().normalize().toString(),
        snapshot,
        assertionStyle
    );

    IOException lastIo = null;
    InterruptedException lastInterrupted = null;
    RuntimeException lastRuntime = null;
    int attempts = Math.max(1, maxRetry + 1);
    for (int attempt = 1; attempt <= attempts; attempt++) {
      try {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.toJson(payload)))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          throw new IOException("LLM HTTP status: " + response.statusCode());
        }
        return Objects.requireNonNull(json.fromJson(response.body(), LlmTestResponse.class));
      } catch (IOException e) {
        lastIo = e;
      } catch (InterruptedException e) {
        lastInterrupted = e;
      } catch (RuntimeException e) {
        lastRuntime = e;
      }
    }

    if (lastInterrupted != null) {
      throw lastInterrupted;
    }
    if (lastIo != null) {
      throw lastIo;
    }
    throw new RuntimeException("LLM call failed", lastRuntime);
  }
}
