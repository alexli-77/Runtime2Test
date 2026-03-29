import requests
import json

# 你的 ngrok 地址
URL = "https://explicable-inga-unapprehendably.ngrok-free.dev/generation"

headers = {
    "Content-Type": "application/json",
    "ngrok-skip-browser-warning": "69420"
}

# 这里必须用 input
payload = {
    "input": "public class Calculator { public int add(int a, int b) { return a + b; } }"
}

print("sending request...")

try:
    response = requests.post(URL, headers=headers, json=payload, timeout=120)
    print("status:", response.status_code)
    print("body-prefix:", response.text[:300])
except Exception as e:
    print("error:", repr(e))
