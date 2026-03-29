package org.threeTesters.runtime2test.engine.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationConnectivityTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void generationEndpointIsReachable() throws Exception {
    String endpoint = System.getProperty("rtf.llm.endpoint", "").trim();
    assumeTrue(!endpoint.isBlank(),
        "Set -Drtf.llm.endpoint=https://<your-ngrok-domain>/generation to run this test");

    URI uri = endpoint.contains("://") ? URI.create(endpoint) : URI.create("https://" + endpoint);

    Map<String, Object> payload = Map.of(
        "input",
        "public class Calculator { public int add(int a, int b) { return a + b; } }"
    );

    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(120))
        .header("Content-Type", "application/json")
        .header("ngrok-skip-browser-warning",
            System.getProperty("rtf.ngrokSkipBrowserWarning", "69420"))
        .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
        .build();

    HttpResponse<String> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());

    assertEquals(200, response.statusCode(), "Generation endpoint returned non-200 response");

    Map<String, Object> body = OBJECT_MAPPER.readValue(
        response.body(), new TypeReference<Map<String, Object>>() {
        }
    );

    assertTrue(
        body.containsKey("Result")
            || (body.containsKey("success") && body.containsKey("files") && body.containsKey("message")),
        "Unexpected response shape. Expected legacy model_server keys (Result) or Runtime2Test keys "
            + "(success/message/files). Body=" + response.body()
    );

    if (body.containsKey("Result")) {
      assertNotNull(body.get("Result"), "Result field is null");
      assertFalse(body.get("Result").toString().isBlank(), "Result field is blank");
    }
  }
}
