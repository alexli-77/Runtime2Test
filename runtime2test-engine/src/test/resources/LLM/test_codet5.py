from transformers import RobertaTokenizer, T5ForConditionalGeneration

# 不再使用 AutoTokenizer，直接指定 RobertaTokenizer
model_name = "Salesforce/codet5-base"

print("正在加载 Tokenizer...")
# 强制直接调用 Roberta 的分词器
tokenizer = RobertaTokenizer.from_pretrained(model_name)
model = T5ForConditionalGeneration.from_pretrained(model_name)

# 确保你的 test_codet5.py 包含这一段推理代码
input_text = "def greet(user): print(f'Hello, {user}!')"
input_ids = tokenizer(input_text, return_tensors="pt").input_ids

# 设定简单的生成参数
print("--- 正在生成 ---")
generated_ids = model.generate(input_ids, max_length=30)
output = tokenizer.decode(generated_ids[0], skip_special_tokens=True)
print(f"输入: {input_text}")
print(f"生成结果: {output}")