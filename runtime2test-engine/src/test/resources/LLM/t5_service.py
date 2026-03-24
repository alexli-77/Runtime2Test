from flask import Flask, request, jsonify
from transformers import RobertaTokenizer, T5ForConditionalGeneration
import torch

app = Flask(__name__)

# 加载模型和分词器
model_name = "Salesforce/codet5-base"
tokenizer = RobertaTokenizer.from_pretrained(model_name)
model = T5ForConditionalGeneration.from_pretrained(model_name)

@app.route('/generate', methods=['POST'])
def generate():
    data = request.json
    input_code = data.get("code", "")
    
    # 执行推理
    input_ids = tokenizer(input_code, return_tensors="pt").input_ids
    generated_ids = model.generate(input_ids, max_length=128)
    result = tokenizer.decode(generated_ids[0], skip_special_tokens=True)
    
    return jsonify({"generated_code": result})

if __name__ == '__main__':
    print("CodeT5 服务已启动，监听端口 5000...")
    app.run(port=5000)