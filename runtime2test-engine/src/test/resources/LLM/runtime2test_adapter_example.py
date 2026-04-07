from flask import Flask, jsonify, request
import os
import torch
from transformers import AutoTokenizer, BitsAndBytesConfig, LlamaForCausalLM


app = Flask(__name__)

MODEL_PATH = os.getenv("R2T_MODEL_PATH", "codellama/CodeLlama-7b-Instruct-hf")
MAX_NEW_TOKENS = int(os.getenv("R2T_MAX_NEW_TOKENS", "256"))
USE_8BIT = os.getenv("R2T_USE_8BIT", "true").lower() == "true"
OUTPUT_PATH = os.getenv("R2T_OUTPUT_PATH", "se/generated/HybridRockyTest.java")


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
    if torch.cuda.is_available():
        inputs = {key: value.to("cuda") for key, value in inputs.items()}

    with torch.no_grad():
        outputs = model.generate(**inputs, max_new_tokens=MAX_NEW_TOKENS)
    return tokenizer.decode(outputs[0], skip_special_tokens=True)


def build_prompt_from_runtime2test_payload(payload: dict) -> str:
    mode = payload.get("mode", "UNKNOWN")
    project_path = payload.get("projectPath", "")
    assertion_style = payload.get("assertionStyle", "JUNIT")
    static_snapshot = payload.get("staticSnapshot", "")
    runtime_facts = payload.get("runtimeFacts", "")
    max_methods = payload.get("maxMethods", "")
    max_facts_per_method = payload.get("maxFactsPerMethod", "")
    include_raw_events = payload.get("includeRawEvents", False)

    return (
        f"mode={mode}\n"
        f"projectPath={project_path}\n"
        f"assertionStyle={assertion_style}\n"
        f"maxMethods={max_methods}\n"
        f"maxFactsPerMethod={max_facts_per_method}\n"
        f"includeRawEvents={include_raw_events}\n"
        f"staticSnapshot:\n{static_snapshot}\n"
        f"runtimeFacts:\n{runtime_facts}\n"
        "Generate JUnit tests as Java source files."
    )


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
