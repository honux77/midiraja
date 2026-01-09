#!/usr/bin/env python3
import os
import sys
from mcp.server.fastmcp import FastMCP
from mlx_lm import load, generate

# 1. FastMCP 서버 초기화
mcp = FastMCP("MLX Local Coder")

# 코딩에 특화된 가벼운 모델 (GPU 메모리 점유가 적고 빠릅니다)
MODEL_ID = "mlx-community/Qwen2.5-Coder-3B-Instruct-4bit"

# 모델 로딩 상태 관리
model = None
tokenizer = None

def ensure_model_loaded():
    global model, tokenizer
    if model is None:
        # MCP 통신은 stdout을 사용하므로, 로그는 반드시 stderr로 출력해야 합니다.
        sys.stderr.write(f"[*] Initializing MLX GPU engine with {MODEL_ID}...\n")
        sys.stderr.flush()
        try:
            model, tokenizer = load(MODEL_ID)
            sys.stderr.write("[*] Model loaded successfully!\n")
        except Exception as e:
            sys.stderr.write(f"[!] Failed to load model: {e}\n")
        sys.stderr.flush()

@mcp.tool()
def local_mlx_coder(prompt: str) -> str:
    """
    Delegate coding tasks to a local MLX LLM on Apple Silicon.
    Use this for generating boilerplate, simple functions, or regex patterns.
    """
    ensure_model_loaded()
    
    # 코딩 최적화 프롬프트 (Few-shot 방식으로 강력한 패턴 주입)
    messages = [
        {"role": "system", "content": "You are a raw Java code generator for the Midiraja project. You output ONLY code. No markdown. No chatter. ALWAYS use Allman brace style (opening brace on a new line)."},
        # 첫 번째 예시 (Few-shot)
        {"role": "user", "content": "Create a simple point record with a move method."},
        {"role": "assistant", "content": (
            "public record Point(int x, int y)\n"
            "{\n"
            "    public Point move(int dx, int dy)\n"
            "    {\n"
            "        return new Point(x + dx, y + dy);\n"
            "    }\n"
            "}"
        )},
        # 실제 사용자 요청
        {"role": "user", "content": prompt}
    ]
    
    formatted_prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    
    sys.stderr.write(f"[*] Local MLX is thinking (Few-shot mode)...\n")
    sys.stderr.flush()
    
    try:
        response = generate(model, tokenizer, prompt=formatted_prompt, max_tokens=1024, verbose=False)
        
        # Strip everything except code
        final_code = response.strip().replace("```java", "").replace("```", "").strip()
        return final_code
    except Exception as e:
        return f"Error during local inference: {str(e)}"

@mcp.tool()
def local_mlx_reviewer(code: str) -> str:
    """
    Delegate code review tasks to a local MLX LLM.
    Use this to review code snippets against Midiraja's specific engineering standards.
    """
    ensure_model_loaded()
    
    # 리뷰 전용 프롬프트 (Midiraja 표준 기준)
    messages = [
        {"role": "system", "content": (
            "You are a strict, senior code reviewer for the Midiraja project. "
            "Review the provided Java code against these EXACT Engineering Standards:\n"
            "1. Brace Style: MUST use Allman style (opening braces '{' on a new line).\n"
            "2. Indentation: MUST be exactly 4 spaces (no tabs).\n"
            "3. Modern Java: MUST use records, sealed interfaces, and var where appropriate.\n"
            "4. GraalVM Safety: MUST NOT use reflection (java.lang.reflect) or dynamic proxies.\n\n"
            "Output format:\n"
            "- If perfect: Respond with 'PASS: Code meets all standards.'\n"
            "- If issues exist: List them clearly as bullet points."
        )},
        {"role": "user", "content": f"Review this code:\n\n{code}"}
    ]
    
    formatted_prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    
    sys.stderr.write(f"[*] Local MLX is reviewing code...\n")
    sys.stderr.flush()
    
    try:
        response = generate(model, tokenizer, prompt=formatted_prompt, max_tokens=1024, verbose=False)
        return response.strip()
    except Exception as e:
        return f"Error during local review: {str(e)}"

if __name__ == "__main__":
    mcp.run()
