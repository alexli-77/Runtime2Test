from flask import Flask, jsonify, request
import os
import torch
from transformers import AutoTokenizer, BitsAndBytesConfig, LlamaForCausalLM


app = Flask(__name__)

MODEL_PATH = os.getenv("R2T_MODEL_PATH", "codellama/CodeLlama-7b-Instruct-hf")
MAX_NEW_TOKENS = int(os.getenv("R2T_MAX_NEW_TOKENS", "256"))
USE_8BIT = os.getenv("R2T_USE_8BIT", "true").lower() == "true"
OUTPUT_PATH = os.getenv("R2T_OUTPUT_PATH", "se/generated/HybridRockyTest.java")
MAX_INPUT_TOKENS = int(os.getenv("R2T_MAX_INPUT_TOKENS", "1536"))
STATIC_SNAPSHOT_TOKENS = int(os.getenv("R2T_STATIC_SNAPSHOT_TOKENS", "900"))
RUNTIME_FACTS_TOKENS = int(os.getenv("R2T_RUNTIME_FACTS_TOKENS", "420"))


def _build_quant_config():
    if not USE_8BIT:
        return None
    return BitsAndBytesConfig(
        load_in_8bit=True,
        llm_int8_enable_fp32_cpu_offload=True,
    )


print(f"Loading model from {MODEL_PATH} (this may take several minutes)...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH)
model = LlamaForCausalLM.from_pretrained(
    MODEL_PATH,
    quantization_config=_build_quant_config(),
    device_map="auto",
    torch_dtype=torch.float16,
)


def generate(prompt: str) -> str:
    inputs = tokenizer(prompt, return_tensors="pt")
    input_len = int(inputs["input_ids"].shape[-1])
    if torch.cuda.is_available():
        inputs = {key: value.to("cuda") for key, value in inputs.items()}

    with torch.no_grad():
        outputs = model.generate(**inputs, max_new_tokens=MAX_NEW_TOKENS)
    generated = outputs[0][input_len:]
    return tokenizer.decode(generated, skip_special_tokens=True).strip()


def _truncate_to_tokens(text: str, max_tokens: int) -> str:
    if not text or max_tokens <= 0:
        return ""

    token_ids = tokenizer.encode(text, add_special_tokens=False)
    if len(token_ids) <= max_tokens:
        return text

    truncated = tokenizer.decode(token_ids[:max_tokens], skip_special_tokens=True)
    return truncated + "\n...\n[TRUNCATED_FOR_TOKEN_BUDGET]"


def _build_system_prompt(assertion_style: str) -> str:
    return (
        "You are an expert Java unit test generator.\n"
        "Goal: generate a complete JUnit 5 test source file for the target method.\n"
        "Hard constraints:\n"
        "1. Return ONLY valid Java code, no markdown, no explanation.\n"
        "2. Include package declaration and all required imports.\n"
        "3. Keep tests executable and deterministic.\n"
        "4. Use Mockito when mocking is needed.\n"
        "5. Do not modify production code.\n"
        f"6. Assertion style preference: {assertion_style}.\n"
    )


def _build_initial_prompt(payload: dict) -> str:
    mode = payload.get("mode", "UNKNOWN")
    project_path = payload.get("projectPath", "")
    assertion_style = payload.get("assertionStyle", "ASSERT_J_DEEP")
    static_snapshot = _truncate_to_tokens(
        payload.get("staticSnapshot", ""),
        STATIC_SNAPSHOT_TOKENS,
    )

    prompt = (
        "[TASK]\n"
        "Generate one whole JUnit 5 test file for methods in the provided context.\n\n"
        "[TARGET]\n"
        f"mode={mode}\n"
        f"projectPath={project_path}\n"
        f"assertionStyle={assertion_style}\n\n"
        "[STATIC_SNAPSHOT]\n"
        f"{static_snapshot}\n\n"
    )

    if mode == "HYBRID_DYNAMIC":
        runtime_facts = _truncate_to_tokens(
            payload.get("runtimeFacts", ""),
            RUNTIME_FACTS_TOKENS,
        )
        prompt += (
            "[RUNTIME_FACTS]\n"
            f"maxMethods={payload.get('maxMethods', '')}\n"
            f"maxFactsPerMethod={payload.get('maxFactsPerMethod', '')}\n"
            f"includeRawEvents={payload.get('includeRawEvents', False)}\n"
            f"{runtime_facts}\n\n"
        )

    prompt += (
        "[OUTPUT_REQUIREMENTS]\n"
        "- Return one complete Java test source file only.\n"
        "- Use class name suffix RockyTest.\n"
        "- Cover normal path and important edge/error branches when possible.\n"
        "- Prefer concise, maintainable tests over redundant cases.\n"
    )
    return prompt


def _build_repair_prompt(payload: dict) -> str:
    unit_test = _truncate_to_tokens(payload.get("unitTest", ""), 700)
    error_message = _truncate_to_tokens(payload.get("errorMessage", ""), 350)
    static_snapshot = _truncate_to_tokens(
        payload.get("staticSnapshot", ""),
        380,
    )
    return (
        "[TASK]\n"
        "Fix the failing JUnit 5 test file and return the full corrected Java file.\n\n"
        "[CURRENT_TEST]\n"
        f"{unit_test}\n\n"
        "[ERROR]\n"
        f"{error_message}\n\n"
        "[REFERENCE_CONTEXT]\n"
        f"{static_snapshot}\n\n"
        "[OUTPUT_REQUIREMENTS]\n"
        "- Return only corrected Java code.\n"
        "- Preserve test intent while fixing compile/runtime failures.\n"
    )


def build_prompt_from_runtime2test_payload(payload: dict) -> str:
    assertion_style = payload.get("assertionStyle", "ASSERT_J_DEEP")
    system_prompt = _build_system_prompt(assertion_style)

    if payload.get("errorMessage") and payload.get("unitTest"):
        user_prompt = _build_repair_prompt(payload)
    else:
        user_prompt = _build_initial_prompt(payload)

    prompt = f"{system_prompt}\n{user_prompt}"
    return _truncate_to_tokens(prompt, MAX_INPUT_TOKENS)


@app.route("/generation", methods=["POST"])
def completion():
    payload = request.get_json(silent=True) or {}

    try:
        # Legacy clients send {"input": "..."}. Runtime2Test sends structured fields.
        prompt = payload.get("input")
        if not prompt:
            prompt = build_prompt_from_runtime2test_payload(payload)

        result = generate(prompt)

        # Keep both contracts for migration compatibility.
        return jsonify(
            {
                "Result": result,
                "success": True,
                "message": "ok",
                "files": [
                    {
                        "relativePath": OUTPUT_PATH,
                        "content": result,
                    }
                ],
            }
        ), 200
    except Exception as exc:
        return (
            jsonify(
                {
                    "success": False,
                    "message": str(exc),
                    "files": [],
                }
            ),
            200,
        )


if __name__ == "__main__":
    print("Runtime2Test adapter server started on port 1234")
    app.run(host="0.0.0.0", port=1234)
