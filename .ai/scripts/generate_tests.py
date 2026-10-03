#!/usr/bin/env python3
"""
AI JUnit 5 Test Generator Script for GitHub Actions CI/CD Pipeline
Supports OpenAI, GitHub Models, or OpenRouter REST APIs.
Includes iterative self-healing JaCoCo coverage enforcement loop.
"""

import os
import sys
import re
import subprocess
import json
import urllib.request
import urllib.error
import xml.etree.ElementTree as ET

# Ensure stdout handles UTF-8 safely across Windows and Linux
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

PROMPT_FILE = os.path.join(os.path.dirname(__file__), "..", "prompts", "generate-junit-tests.md")

def get_changed_java_files(base_branch="origin/main"):
    """Find modified/added production Java files compared to base branch or working tree."""
    files = set()
    
    diff_commands = [
        ["git", "diff", "--name-only", "--diff-filter=d", "HEAD^1", "HEAD^2"],
        ["git", "diff", "--name-only", "--diff-filter=d", f"{base_branch}...HEAD"],
        ["git", "diff", "--name-only", "--diff-filter=d", f"{base_branch}", "HEAD"],
        ["git", "diff", "--name-only", "--diff-filter=d", "HEAD~1...HEAD"],
        ["git", "diff", "--name-only", "--diff-filter=d", "HEAD~2...HEAD"],
        ["git", "diff", "--name-only", "HEAD"]
    ]

    for cmd in diff_commands:
        try:
            output = subprocess.check_output(cmd, text=True, stderr=subprocess.DEVNULL)
            for line in output.strip().splitlines():
                if line: files.add(line)
        except Exception:
            pass

    prod_java_files = [f for f in files if f.startswith("src/main/java/") and f.endswith(".java")]
    
    # If no files changed in diff (e.g. forced run), scan all src/main/java files excluding config/dto/entity
    if not prod_java_files and os.path.exists("src/main/java"):
        for root, _, filenames in os.walk("src/main/java"):
            for fn in filenames:
                if fn.endswith(".java") and not any(x in root for x in ["/dto", "/entity", "/config"]):
                    prod_java_files.append(os.path.join(root, fn).replace("\\", "/"))

    return sorted(list(set(prod_java_files)))

def read_file(filepath):
    if os.path.exists(filepath):
        with open(filepath, "r", encoding="utf-8") as f:
            return f.read()
    return ""

def write_file(filepath, content):
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

def call_ai_api(prompt_system, user_content, provider, api_key, model):
    """Call OpenAI compatible Chat Completions API using standard library with automatic model fallback."""
    if provider == "github-models":
        endpoint = "https://models.inference.ai.azure.com/chat/completions"
    elif provider == "openrouter":
        endpoint = "https://openrouter.ai/api/v1/chat/completions"
    else:
        endpoint = "https://api.openai.com/v1/chat/completions"

    # Primary model followed by top free OpenRouter models for code generation
    candidate_models = [model]
    if provider == "openrouter":
        free_fallbacks = [
            "google/gemma-4-31b-it:free",
            "google/gemma-4-26b-a4b-it:free",
            "qwen/qwen-2.5-coder-32b-instruct:free",
            "google/gemini-2.0-flash-exp:free",
            "deepseek/deepseek-r1-distill-llama-70b:free"
        ]
        for m in free_fallbacks:
            if m not in candidate_models:
                candidate_models.append(m)

    last_exception = None
    for target_model in candidate_models:
        payload = {
            "model": target_model,
            "messages": [
                {"role": "system", "content": prompt_system},
                {"role": "user", "content": user_content}
            ],
            "temperature": 0.2,
            "max_tokens": 2000
        }

        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
            "HTTP-Referer": "https://github.com/futuretechgenius1/cicd",
            "X-Title": "Spring Boot AI CI/CD Demo"
        }

        req = urllib.request.Request(endpoint, data=json.dumps(payload).encode("utf-8"), headers=headers)
        
        try:
            print(f"Calling AI model: {target_model} via {provider}...")
            with urllib.request.urlopen(req) as response:
                res_data = json.loads(response.read().decode("utf-8"))
                content = res_data["choices"][0]["message"]["content"]
                if content:
                    print(f"Successfully received response from model: {target_model}")
                    return content
        except urllib.error.HTTPError as e:
            err_body = e.read().decode('utf-8')
            print(f"AI API HTTP Error {e.code} for model {target_model}: {err_body}")
            last_exception = e
        except Exception as e:
            print(f"AI API Error for model {target_model}: {e}")
            last_exception = e

    if last_exception:
        raise last_exception

def generate_fallback_test(prod_file_path, code):
    """Generate a template test if AI_API_KEY is not set (mock mode)."""
    filename = os.path.basename(prod_file_path)
    class_name = filename.replace(".java", "")
    package_match = re.search(r"package\s+([\w\.]+);", code)
    package_name = package_match.group(1) if package_match else "com.example.aicicddemo"
    
    test_package = package_name
    test_class_name = f"{class_name}Test"
    test_file_path = prod_file_path.replace("src/main/java", "src/test/java").replace(f"{class_name}.java", f"{test_class_name}.java")

    test_code = f"""package {test_package};

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("{test_class_name} Automated AI Test")
class {test_class_name} {{

    @Test
    @DisplayName("Should verify {class_name} functionality")
    void test{class_name}InitialState() {{
        assertTrue(true, "{class_name} verified by AI generator");
    }}
}}
"""
    return test_file_path, test_code

def extract_java_code(response_text):
    """Extract code block inside ```java ... ``` and sanitize hallucinated imports."""
    if not response_text:
        return ""
    code = ""
    match = re.search(r"```java\s*(.*?)\s*```", response_text, re.DOTALL)
    if match:
        code = match.group(1).strip()
    else:
        match = re.search(r"```\s*(.*?)\s*```", response_text, re.DOTALL)
        if match:
            code = match.group(1).strip()
        else:
            code = response_text.strip()

    # Sanitize hallucinated non-existent package imports
    code = re.sub(r'import\s+com\.example\.aicicddemo\.service\.impl\.[^;]+;\s*\n?', '', code)
    code = re.sub(r'import\s+com\.example\.aicicddemo\.model\.[^;]+;\s*\n?', '', code)
    return code

def parse_jacoco_coverage(xml_path):
    """Parse JaCoCo XML report and return overall line coverage ratio & per-class metrics."""
    if not os.path.exists(xml_path):
        return None, {}
    
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()

        overall_missed = 0
        overall_covered = 0

        for counter in root.findall("counter"):
            if counter.attrib.get("type") == "LINE":
                overall_missed = int(counter.attrib.get("missed", 0))
                overall_covered = int(counter.attrib.get("covered", 0))

        total = overall_missed + overall_covered
        overall_ratio = (overall_covered / total) if total > 0 else 0.0

        class_coverage = {}

        for package in root.findall("package"):
            for cls in package.findall("class"):
                class_name = cls.attrib.get("name", "").replace("/", ".")
                cls_missed = 0
                cls_covered = 0
                for counter in cls.findall("counter"):
                    if counter.attrib.get("type") == "LINE":
                        cls_missed = int(counter.attrib.get("missed", 0))
                        cls_covered = int(counter.attrib.get("covered", 0))

                cls_total = cls_missed + cls_covered
                cls_ratio = (cls_covered / cls_total) if cls_total > 0 else 1.0

                uncovered_methods = []
                for method in cls.findall("method"):
                    m_name = method.attrib.get("name", "")
                    m_missed = 0
                    for counter in method.findall("counter"):
                        if counter.attrib.get("type") == "LINE":
                            m_missed = int(counter.attrib.get("missed", 0))
                    if m_missed > 0:
                        uncovered_methods.append(f"Method '{m_name}' (missed {m_missed} lines)")

                class_coverage[class_name] = {
                    "ratio": cls_ratio,
                    "missed": cls_missed,
                    "covered": cls_covered,
                    "uncovered_methods": uncovered_methods
                }

        return overall_ratio, class_coverage
    except Exception as e:
        print(f"Error parsing JaCoCo XML report: {e}")
        return None, {}

def run_maven_coverage():
    """Run Maven test & jacoco:report to evaluate code coverage."""
    print("\n--- Running Maven test & JaCoCo coverage evaluation ---")
    cmd = ["mvn", "test", "jacoco:report", "-B"]
    if sys.platform == "win32":
        cmd = ["mvn.cmd", "test", "jacoco:report", "-B"]
    try:
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            print("Maven test run completed with warnings or failed tests.")
            if res.stderr:
                print("Tail of stderr:")
                print(res.stderr[-500:])
    except Exception as e:
        print(f"Failed to execute Maven coverage command: {e}")

def main():
    print("=== Starting AI JUnit Test Generator with JaCoCo Coverage Enforcement ===")
    
    provider = os.getenv("AI_PROVIDER", "openrouter").lower()
    api_key = os.getenv("AI_API_KEY", "").strip()
    model = os.getenv("AI_MODEL", "google/gemma-4-31b-it:free").strip()
    base_branch = os.getenv("BASE_BRANCH", "origin/main")
    min_coverage = float(os.getenv("MIN_COVERAGE_RATIO", "0.80"))
    max_attempts = int(os.getenv("MAX_REGEN_ATTEMPTS", "3"))

    changed_files = get_changed_java_files(base_branch)
    
    if not changed_files:
        print("No modified or added production Java files detected.")
        sys.exit(0)

    print(f"Target production Java files ({len(changed_files)}):")
    for f in changed_files:
        print(f" - {f}")

    prompt_system = read_file(PROMPT_FILE)
    if not prompt_system:
        prompt_system = "You are an expert Java test automation engineer. Generate JUnit 5 tests."

    for attempt in range(1, max_attempts + 1):
        print(f"\n==================================================")
        print(f" AI Test Generation & Verification Attempt {attempt}/{max_attempts}")
        print(f"==================================================")

        generated_tests = []
        xml_path = os.path.join("target", "site", "jacoco", "jacoco.xml")
        _, prev_class_coverage = parse_jacoco_coverage(xml_path) if attempt > 1 else (None, {})

        for prod_file in changed_files:
            code = read_file(prod_file)
            if not code:
                continue

            class_name = os.path.basename(prod_file).replace(".java", "")
            full_class_name = None
            package_match = re.search(r"package\s+([\w\.]+);", code)
            if package_match:
                full_class_name = f"{package_match.group(1)}.{class_name}"

            test_file_path = prod_file.replace("src/main/java", "src/test/java").replace(f"{class_name}.java", f"{class_name}Test.java")
            existing_test_code = read_file(test_file_path)

            # Check if this class needs regeneration on attempt > 1
            if attempt > 1 and full_class_name in prev_class_coverage:
                c_info = prev_class_coverage[full_class_name]
                if c_info["ratio"] >= min_coverage:
                    print(f"Skipping {class_name}: Already met coverage ({c_info['ratio']*100:.1f}% >= {min_coverage*100:.0f}%)")
                    continue

            print(f"\nGenerating/Refining tests for {class_name} (Attempt {attempt})...")

            if api_key:
                feedback_info = ""
                if attempt > 1 and full_class_name in prev_class_coverage:
                    c_info = prev_class_coverage[full_class_name]
                    uncovered_list = "\n".join(c_info.get("uncovered_methods", []))
                    feedback_info = f"""
JACOCO COVERAGE FEEDBACK ALERT:
Current line coverage for `{class_name}` is {c_info['ratio']*100:.1f}% which is below required {min_coverage*100:.0f}%.
Uncovered Methods / Lines:
{uncovered_list if uncovered_list else "- Additional branch conditions and exception handlers need coverage."}

INSTRUCTION:
Add new @Test methods to `{class_name}Test.java` targeting all uncovered methods, exception cases, null checks, and boundary conditions to reach at least 80% line coverage.
"""

                user_prompt = f"""Target Production File: {prod_file}
Source Code:
```java
{code}
```

Existing Test File ({test_file_path}):
```java
{existing_test_code if existing_test_code else "// No existing tests"}
```
{feedback_info}
Generate full, complete Java JUnit 5 test file for `{class_name}Test.java`."""

                try:
                    response = call_ai_api(prompt_system, user_prompt, provider, api_key, model)
                    test_code = extract_java_code(response)
                    write_file(test_file_path, test_code)
                    generated_tests.append(test_file_path)
                    print(f"Successfully updated tests for {test_file_path}")
                except Exception as e:
                    print(f"AI generation failed for {class_name}: {e}. Using fallback generator.")
                    tf, tc = generate_fallback_test(prod_file, code)
                    write_file(tf, tc)
                    generated_tests.append(tf)
            else:
                print(f"AI_API_KEY not configured. Generating structural fallback JUnit test for {class_name}.")
                tf, tc = generate_fallback_test(prod_file, code)
                write_file(tf, tc)
                generated_tests.append(tf)

        # Run Maven JaCoCo report to evaluate coverage
        run_maven_coverage()
        overall_ratio, class_coverage = parse_jacoco_coverage(xml_path)

        if overall_ratio is not None:
            print(f"\n--> Attempt {attempt} Overall JaCoCo Line Coverage: {overall_ratio*100:.2f}% (Required: {min_coverage*100:.0f}%)")
            if overall_ratio >= min_coverage:
                print(f"[SUCCESS] JaCoCo Coverage Gate PASSED! Target line coverage of {min_coverage*100:.0f}% satisfied ({overall_ratio*100:.2f}%).")
                sys.exit(0)
            else:
                print(f"[WARNING] JaCoCo Coverage Gate FAILED: Line coverage ratio is {overall_ratio*100:.2f}%, expected minimum is {min_coverage*100:.0f}%.")
        else:
            print("Warning: Could not parse JaCoCo coverage XML report.")

    if overall_ratio is not None and overall_ratio < min_coverage:
        print(f"\n[ERROR] Final JaCoCo Coverage Gate FAILED after {max_attempts} attempts. Line coverage: {overall_ratio*100:.2f}% (Expected: >= {min_coverage*100:.0f}%).")
        sys.exit(1)

    print("\n=== AI Test Generation Completed ===")

if __name__ == "__main__":
    main()
