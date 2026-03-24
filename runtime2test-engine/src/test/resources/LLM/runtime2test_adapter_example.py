from flask import Flask, request, jsonify
from transformers import RobertaTokenizer, T5ForConditionalGeneration

app = Flask(__name__)

model_name = "Salesforce/codet5-base"
tokenizer = RobertaTokenizer.from_pretrained(model_name)
model = T5ForConditionalGeneration.from_pretrained(model_name)


def generate(prompt: str) -> str:
    input_ids = tokenizer(prompt, return_tensors="pt").input_ids
    generated_ids = model.generate(input_ids, max_length=256)
    return tokenizer.decode(generated_ids[0], skip_special_tokens=True)


def build_prompt_from_runtime2test_payload(payload: dict) -> str:
    mode = payload.get("mode", "UNKNOWN")
    project_path = payload.get("projectPath", "")
    assertion_style = payload.get("assertionStyle", "JUNIT")
    static_snapshot = payload.get("staticSnapshot", "")
    runtime_facts = payload.get("runtimeFacts", "")

    return (
        f"mode={mode}\n"
        f"projectPath={project_path}\n"
        f"assertionStyle={assertion_style}\n"
        f"staticSnapshot:\n{static_snapshot}\n"
        f"runtimeFacts:\n{runtime_facts}\n"
        "Generate JUnit tests as Java source files."
    )


@app.route('/generation', methods=['POST'])
def completion():
    payload = request.get_json(silent=True) or {}

    try:
        # Backward-compatible path for legacy clients.
        if "input" in payload:
            prompt = payload["input"]
        else:
            prompt = build_prompt_from_runtime2test_payload(payload)

        result = generate(prompt)

        # Runtime2Test response contract.
        return jsonify({
            "success": True,
            "message": "ok",
            "files": [
                {
                    "relativePath": "se/generated/HybridRockyTest.java",
                    "content": result,
                }
            ],
        }), 200

    except Exception as exc:
        return jsonify({
            "success": False,
            "message": str(exc),
            "files": [],
        }), 200


if __name__ == '__main__':
    print("Runtime2Test adapter server started on port 5000")
    app.run(host="0.0.0.0", port=5000)
