# Runtime2Test Model Server Contract

This document defines the HTTP contract expected by Runtime2Test when using remote LLM generation.

## Endpoint

Use a full endpoint URL in config, for example:

- `https://<your-ngrok-domain>/generation`

Runtime2Test sends a `POST` request with `Content-Type: application/json`.

## Request: `LLM_FIRST_STATIC`

```json
{
  "mode": "LLM_FIRST_STATIC",
  "projectPath": "/absolute/path/to/project",
  "staticSnapshot": "{...json string...}",
  "assertionStyle": "ASSERT_J_DEEP"
}
```

## Request: `HYBRID_DYNAMIC`

```json
{
  "mode": "HYBRID_DYNAMIC",
  "projectPath": "/absolute/path/to/project",
  "staticSnapshot": "{...json string...}",
  "runtimeFacts": "{...json string...}",
  "assertionStyle": "ASSERT_J_DEEP",
  "maxMethods": 100,
  "maxFactsPerMethod": 20,
  "includeRawEvents": false
}
```

## Response (required)

Your server must return this JSON shape:

```json
{
  "success": true,
  "message": "ok",
  "files": [
    {
      "relativePath": "se/example/GeneratedRockyTest.java",
      "content": "...full java source..."
    }
  ]
}
```

## Field requirements

1. `success`: Boolean.
2. `message`: String with summary or error information.
3. `files`: Array of generated files. Can be empty if `success` is `false`.
4. `relativePath`: Must be a relative path under test source root.
5. `content`: Full Java file content.

## Compatibility note

Existing simple model services often use a different contract (for example `input` -> `Result`).
To integrate with Runtime2Test, add a compatibility layer that maps Runtime2Test requests to your model prompt format and maps model output back to `success/message/files`.

## Validation checklist

1. Endpoint path matches config (`/generation`).
2. Response JSON keys exactly match expected names.
3. `files[].relativePath` is not empty.
4. `files[].content` is valid Java source text.
5. Non-2xx responses should be avoided; return `success=false` with an error message in the JSON body when possible.
