import requests
import json

--- 你的 ngrok 地址 ---
URL = "https://explicable-inga-unapprehendably.ngrok-free.dev/generation"

headers = {
"Content-Type": "application/json",
"ngrok-skip-browser-warning": "69420"
}

注意：这里必须用 "input"，因为你的 model_server.py 只认这个词
payload = {
"input": "public class Calculator { public int add(int a, int b) { return a + b; } }"
}

print("🚀 正在发送请求到模型...")

try:
response = requests.post(URL, headers=headers, json=payload, timeout=120)

except Exception as e:
print(f"🔥 发生错误: {e}")
