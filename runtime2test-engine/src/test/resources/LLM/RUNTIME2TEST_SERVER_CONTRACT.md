# Runtime2Test Model Server Contract

This document defines the HTTP contract expected by Runtime2Test when using remote LLM generation.

## Endpoint

Use a full endpoint URL in config, for example:

- `https://<your-cloudflare-domain>/generation`

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

## Prompt template strategy (ChatUniTest style)

To mimic ChatUniTest-style prompting while keeping Runtime2Test compatibility, structure prompts in layers:

1. **System template**: role, hard constraints, output format.
2. **Initial template**: target context (`mode`, `projectPath`, `assertionStyle`) plus static and runtime summaries.
3. **Repair template**: failing test + compiler/runtime error + minimal target context.

Recommended behavior:

- Use `LLM_FIRST_STATIC` -> **system + initial(static)**.
- Use `HYBRID_DYNAMIC` -> **system + initial(static + runtimeFacts)**.
- If iterative repair is enabled by your server workflow -> **system + repair**.

## Token budget guidance

Even when model context is large, use explicit budget controls to avoid unstable generation and truncation.

Suggested defaults for CodeLlama-style LoRA models:

1. Prompt total budget: `1200-1600` tokens.
2. `staticSnapshot` section: `700-1000` tokens.
3. `runtimeFacts` section: `300-500` tokens.
4. Keep output headroom for generated Java file (`300+` tokens minimum).

In the sample adapter (`runtime2test_adapter_example.py`), the following env vars are provided for this:

- `R2T_MAX_INPUT_TOKENS` (default `1536`)
- `R2T_STATIC_SNAPSHOT_TOKENS` (default `900`)
- `R2T_RUNTIME_FACTS_TOKENS` (default `420`)

## One-request-per-method workflow

If you want one LLM request per target method (for richer per-method context), use:

- `method_level_scheduler.py`

Path:

- `runtime2test-engine/src/test/resources/LLM/method_level_scheduler.py`

What it does:

1. Reads `static-snapshot.json` and `invocations.json` from `dataPath`.
2. Builds one payload per method (`maxMethods=1` in hybrid mode).
3. Calls `/generation` for each method.
4. Writes returned `files` under method-specific folders.
5. Optionally runs one validation command and one repair round.

Example:

```bash
python3 runtime2test-engine/src/test/resources/LLM/method_level_scheduler.py \
  --endpoint http://127.0.0.1:1234/generation \
  --data-path /Users/files/code/github/pdfbox/rtf/data \
  --output-dir /Users/files/code/github/pdfbox/rtf/generated-tests/method-level \
  --project-path /Users/files/code/github/pdfbox \
  --mode HYBRID_DYNAMIC \
  --top-k 50 \
  --concurrency 4
```

Optional single-repair mode (one round only) with external validation:

```bash
python3 runtime2test-engine/src/test/resources/LLM/method_level_scheduler.py \
  ... \
  --validate-cmd "javac {test_file}"
```

`--validate-cmd` placeholders:

- `{test_file}`
- `{method_id}`
- `{test_dir}`
