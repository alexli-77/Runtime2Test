package org.threeTesters.runtime2test.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

public class LlmConnectivityTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  void shouldReachRemoteLlmServer() throws Exception {
    String endpoint = firstNonBlank(
        System.getProperty("rtf.cloudflare.endpoint"),
        System.getenv("RTF_CLOUDFLARE_ENDPOINT"),
        System.getProperty("rtf.llm.endpoint"),
        System.getenv("RTF_LLM_ENDPOINT")
    );
    Assumptions.assumeTrue(
        endpoint != null,
        "Missing endpoint. Set -Drtf.llm.endpoint=<url> or RTF_LLM_ENDPOINT."
    );

    String payload = MAPPER.writeValueAsString(
        Map.of("input", "public class Calculator { public int add(int a, int b) { return a + b; } }")
    );

    HttpResponse<String> response = postJson(endpoint, payload);

    System.out.println(response.body().toString());
    assertEquals(200, response.statusCode(), "Unexpected HTTP status. Body: " + response.body());

    JsonNode json = MAPPER.readTree(response.body());
    boolean legacyOk = json.hasNonNull("Result") && !json.path("Result").asText("").isBlank();
    boolean runtime2testContractOk = json.path("success").asBoolean(false)
        && json.path("files").isArray()
        && json.path("files").size() > 0
        && !json.path("files").get(0).path("content").asText("").isBlank();

    assertTrue(
        legacyOk || runtime2testContractOk,
        "Unexpected response contract: " + response.body()
    );
  }

//   @Test
//   void shouldReachRuntime2TestContractEndpoint() throws Exception {
//     String endpoint = firstNonBlank(
//         System.getProperty("rtf.cloudflare.endpoint"),
//         System.getenv("RTF_CLOUDFLARE_ENDPOINT"),
//         System.getProperty("rtf.llm.endpoint"),
//         System.getenv("RTF_LLM_ENDPOINT")
//     );
//     Assumptions.assumeTrue(
//         endpoint != null,
//         "Missing endpoint. Set -Drtf.cloudflare.endpoint=<url> or RTF_CLOUDFLARE_ENDPOINT."
//     );

//     String payload = MAPPER.writeValueAsString(Map.of(
//         "mode", "HYBRID_DYNAMIC",
//         "projectPath", "/tmp/runtime2test-project",
//         "staticSnapshot", "{}",
//         "runtimeFacts", "{}",
//         "assertionStyle", "JUNIT",
//         "maxMethods", 100,
//         "maxFactsPerMethod", 20,
//         "includeRawEvents", false
//     ));

//     HttpResponse<String> response = postJson(endpoint, payload);

//     assertEquals(200, response.statusCode(), "Unexpected HTTP status. Body: " + response.body());

//     JsonNode json = MAPPER.readTree(response.body());
//     assertTrue(json.path("success").isBoolean(), "Missing boolean success field: " + response.body());
//     assertTrue(json.path("files").isArray(), "Missing files array: " + response.body());
//     assertTrue(json.path("success").asBoolean(false), "LLM server returned success=false: " + response.body());
//     assertTrue(json.path("files").size() > 0, "LLM server returned empty files list: " + response.body());
//     assertTrue(
//         !json.path("files").get(0).path("content").asText("").isBlank(),
//         "First file content is blank: " + response.body()
//     );
//   }

  private static HttpResponse<String> postJson(String endpoint, String payload)
      throws java.io.IOException, InterruptedException {
    String resolvedEndpoint = normalizeEndpoint(endpoint);
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(resolvedEndpoint))
        .timeout(Duration.ofSeconds(120))
        .header("Content-Type", "application/json");

    HttpRequest request = builder
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build();

    return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String normalizeEndpoint(String endpoint) {
    String normalized = endpoint.trim();
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
      normalized = "https://" + normalized;
    }
    if (!normalized.endsWith("/generation")) {
      normalized = normalized.endsWith("/") ? normalized + "generation" : normalized + "/generation";
    }
    return normalized;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }
}
