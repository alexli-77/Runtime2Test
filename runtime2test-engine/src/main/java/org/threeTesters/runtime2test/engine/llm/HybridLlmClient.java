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

public class HybridLlmClient {

  private static final String NGROK_SKIP_WARNING_HEADER = "ngrok-skip-browser-warning";
  private static final String NGROK_SKIP_WARNING_VALUE =
      System.getProperty("rtf.ngrokSkipBrowserWarning", "69420");

  private final HttpClient httpClient;
  private final Json json;

  public HybridLlmClient() {
    this.httpClient = HttpClient.newHttpClient();
    this.json = new Json();
  }

  public LlmTestResponse generateTests(
      URI endpoint,
      Path projectPath,
      Path staticSnapshotPath,
      String runtimeFacts,
      String assertionStyle,
      int timeoutMs,
      int maxRetry,
      int maxMethods,
      int maxFactsPerMethod,
      boolean includeRawEvents
  ) throws IOException, InterruptedException {
    Objects.requireNonNull(endpoint);
    String staticSnapshot = Files.readString(staticSnapshotPath);

    HybridLlmTestRequest payload = new HybridLlmTestRequest(
        "HYBRID_DYNAMIC",
        projectPath.toAbsolutePath().normalize().toString(),
        staticSnapshot,
        runtimeFacts,
        assertionStyle,
        maxMethods,
        maxFactsPerMethod,
        includeRawEvents
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
          .header(NGROK_SKIP_WARNING_HEADER, NGROK_SKIP_WARNING_VALUE)
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
