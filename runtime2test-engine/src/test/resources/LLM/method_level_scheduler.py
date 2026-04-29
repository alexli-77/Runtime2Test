#!/usr/bin/env python3
"""Method-level Runtime2Test LLM scheduler.

This script implements a one-request-per-target-method workflow:
1. Build per-method static context from static-snapshot.json.
2. Build per-method runtime facts from invocations.json.
3. Call Runtime2Test model server (/generation) once per method.
4. Persist returned files.
5. Optionally run one validation command and one repair round.

Example:
python3 method_level_scheduler.py \
  --endpoint http://127.0.0.1:1234/generation \
  --data-path /Users/files/code/github/pdfbox/rtf/data \
  --output-dir /Users/files/code/github/pdfbox/rtf/generated-tests/method-level \
  --project-path /Users/files/code/github/pdfbox \
  --mode HYBRID_DYNAMIC \
  --top-k 50 \
  --concurrency 4
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib import error, request


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _sanitize_name(text: str) -> str:
    out = re.sub(r"[^a-zA-Z0-9_.-]+", "_", text)
    return out[:120] if len(out) > 120 else out


def _method_folder(method_id: str) -> str:
    digest = hashlib.sha1(method_id.encode("utf-8")).hexdigest()[:10]
    return f"{_sanitize_name(method_id)}__{digest}"


@dataclass
class MethodFacts:
    invocation_count: int = 0
    void_returns: int = 0
    value_returns: int = 0
    nested_calls: int = 0
    mocked_calls: int = 0
    parameter_shapes: list[str] | None = None
    returned_values: list[str] | None = None
    parameter_values: list[list[str]] | None = None

    def __post_init__(self) -> None:
        if self.parameter_shapes is None:
            self.parameter_shapes = []
        if self.returned_values is None:
            self.returned_values = []
        if self.parameter_values is None:
            self.parameter_values = []


def load_invocation_facts(invocations_path: Path, max_shapes: int) -> tuple[dict[str, MethodFacts], int]:
    per_method: dict[str, MethodFacts] = {}
    total = 0

    with invocations_path.open("r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            total += 1
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue

            method = rec.get("recordedMethod", {})
            method_id = (
                f"{method.get('declaringClassName', '')}#"
                f"{method.get('methodName', '')}("
                f"{','.join(method.get('parameterTypes', []))})"
            )

            if not method_id or method_id == "#()":
                continue

            facts = per_method.setdefault(method_id, MethodFacts())
            facts.invocation_count += 1
            if bool(rec.get("isVoid", False)):
                facts.void_returns += 1
            else:
                facts.value_returns += 1

            nested = rec.get("nestedInvocations") or []
            mocked = rec.get("mockedInvocations") or []
            facts.nested_calls += len(nested)
            facts.mocked_calls += len(mocked)

            params = rec.get("parameters") or []
            shape = ",".join(
                (p.get("staticType") or "unknown") if isinstance(p, dict) else "unknown"
                for p in params
            )
            if shape and shape not in facts.parameter_shapes and len(facts.parameter_shapes) < max(1, max_shapes):
                facts.parameter_shapes.append(shape)

            # Collect real returned values (up to 5 unique)
            returned = rec.get("returned")
            if returned:
                stmts = returned.get("statements") or []
                for s in stmts:
                    if s and s not in facts.returned_values and len(facts.returned_values) < 5:
                        facts.returned_values.append(s)

            # Collect real parameter values (up to 5 unique invocations)
            if params and len(facts.parameter_values) < 5:
                param_vals = [
                    (p.get("statements") or [None])[0]
                    for p in params if isinstance(p, dict)
                ]
                if param_vals and param_vals not in facts.parameter_values:
                    facts.parameter_values.append(param_vals)

    return per_method, total


def build_static_subset(
    snapshot: dict[str, Any],
    method_map: dict[str, dict[str, Any]],
    method_id: str,
    related_limit: int,
) -> dict[str, Any]:
    base = method_map[method_id]
    selected_ids = [method_id]

    related = []
    related.extend(base.get("directCallersInType", []))
    related.extend(base.get("directCalleesInType", []))
    related.extend(base.get("nestedInvocationTargets", []))

    for rid in related:
        if rid in method_map and rid not in selected_ids:
            selected_ids.append(rid)
        if len(selected_ids) >= 1 + max(0, related_limit):
            break

    methods = [method_map[mid] for mid in selected_ids]
    return {
        "schemaVersion": snapshot.get("schemaVersion", 1),
        "generatedAt": snapshot.get("generatedAt"),
        "projectPath": snapshot.get("projectPath"),
        "candidateCount": len(methods),
        "methods": methods,
    }


def build_runtime_facts_payload(
    method_id: str,
    per_method: dict[str, MethodFacts],
    total_invocations: int,
) -> dict[str, Any]:
    facts = per_method.get(method_id, MethodFacts())
    return {
        "totalInvocations": total_invocations,
        "methods": [
            {
                "methodId": method_id,
                "invocationCount": facts.invocation_count,
                "voidReturns": facts.void_returns,
                "valueReturns": facts.value_returns,
                "nestedCalls": facts.nested_calls,
                "mockedCalls": facts.mocked_calls,
                "parameterShapes": facts.parameter_shapes,
                "returnedValues": facts.returned_values,
                "parameterValues": facts.parameter_values,
            }
        ],
        "eventCounts": {},
    }


def post_json(url: str, payload: dict[str, Any], timeout_sec: int) -> dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    req = request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with request.urlopen(req, timeout=timeout_sec) as resp:
        body = resp.read().decode("utf-8")
        return json.loads(body)


def write_files(output_dir: Path, files: list[dict[str, Any]]) -> list[Path]:
    written: list[Path] = []
    for f in files:
        relative_path = (f.get("relativePath") or "").strip()
        content = f.get("content")
        if not relative_path:
            continue
        if content is None:
            content = ""

        target = (output_dir / relative_path).resolve()
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        written.append(target)
    return written


def run_validation(validate_cmd_tpl: str, test_file: Path, method_id: str) -> tuple[bool, str]:
    cmd = validate_cmd_tpl.format(
        test_file=str(test_file),
        method_id=method_id,
        test_dir=str(test_file.parent),
    )
    proc = subprocess.run(
        cmd,
        shell=True,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return proc.returncode == 0, proc.stdout


def _extract_test_methods_from_java(content: str) -> list[str]:
    """Extract individual @Test method blocks from a Java file string."""
    first = content.find("@Test")
    if first == -1:
        return []
    body = content[first:]
    # Remove trailing class closing brace if present
    body = body.rstrip()
    if body.endswith("}"):
        body = body[:-1].rstrip()
    parts = re.split(r'(?=@Test)', body)
    methods = []
    for part in parts:
        part = part.strip()
        if part.startswith("@Test") and "{" in part:
            methods.append(part)
    return methods


def _merge_java_samples(samples: list[str]) -> str:
    """Merge multiple HybridRockyTest Java strings into one, deduplicating by method name."""
    seen_names: set[str] = set()
    unique_methods: list[str] = []
    for sample in samples:
        for method_block in _extract_test_methods_from_java(sample):
            name_match = re.search(r'void\s+(\w+)\s*\(', method_block)
            name = name_match.group(1) if name_match else None
            if name and name in seen_names:
                continue
            if name:
                seen_names.add(name)
            unique_methods.append(method_block)
    if not unique_methods:
        return samples[0] if samples else ""
    indented = "\n\n    ".join(m.replace("\n", "\n    ") for m in unique_methods)
    return (
        "package se.kth.castor.generated;\n\n"
        "import org.junit.jupiter.api.Test;\n"
        "import static org.junit.jupiter.api.Assertions.*;\n\n"
        "public class HybridRockyTest {\n\n"
        f"    {indented}\n\n"
        "}"
    )


def process_one_method(
    endpoint: str,
    method_id: str,
    snapshot: dict[str, Any],
    method_map: dict[str, dict[str, Any]],
    per_method_facts: dict[str, MethodFacts],
    total_invocations: int,
    out_root: Path,
    project_path: str,
    mode: str,
    assertion_style: str,
    max_facts_per_method: int,
    include_raw_events: bool,
    related_limit: int,
    timeout_sec: int,
    validate_cmd_tpl: str | None,
    samples_per_method: int = 1,
) -> dict[str, Any]:
    method_out_dir = out_root / _method_folder(method_id)
    method_out_dir.mkdir(parents=True, exist_ok=True)

    static_subset = build_static_subset(snapshot, method_map, method_id, related_limit)

    payload: dict[str, Any] = {
        "mode": mode,
        "projectPath": project_path,
        "staticSnapshot": json.dumps(static_subset, ensure_ascii=False),
        "assertionStyle": assertion_style,
    }

    if mode == "HYBRID_DYNAMIC":
        runtime_facts_payload = build_runtime_facts_payload(method_id, per_method_facts, total_invocations)
        payload.update(
            {
                "runtimeFacts": json.dumps(runtime_facts_payload, ensure_ascii=False),
                "maxMethods": 1,
                "maxFactsPerMethod": max_facts_per_method,
                "includeRawEvents": include_raw_events,
            }
        )

    # ── Multi-sample generation ──────────────────────────────────────────────
    collected_java: list[str] = []
    for sample_idx in range(max(1, samples_per_method)):
        try:
            response = post_json(endpoint, payload, timeout_sec)
        except (error.URLError, TimeoutError, json.JSONDecodeError) as exc:
            if sample_idx == 0:
                return {
                    "methodId": method_id,
                    "success": False,
                    "message": f"request failed: {exc}",
                    "writtenFiles": [],
                    "repaired": False,
                    "samplesCollected": 0,
                }
            break  # partial success: use what we have

        if not bool(response.get("success", False)):
            if sample_idx == 0:
                return {
                    "methodId": method_id,
                    "success": False,
                    "message": f"llm rejected: {response.get('message', '')}",
                    "writtenFiles": [],
                    "repaired": False,
                    "samplesCollected": 0,
                }
            break

        for f in response.get("files") or []:
            content = f.get("content") or ""
            if content.strip():
                collected_java.append(content)

    if not collected_java:
        return {
            "methodId": method_id,
            "success": False,
            "message": "no content in any sample",
            "writtenFiles": [],
            "repaired": False,
            "samplesCollected": 0,
        }

    # Merge all samples into one file with deduplicated @Test methods
    if len(collected_java) > 1:
        merged_content = _merge_java_samples(collected_java)
    else:
        merged_content = collected_java[0]

    merged_files = [{"relativePath": "se/kth/castor/generated/HybridRockyTest.java", "content": merged_content}]
    written = write_files(method_out_dir, merged_files)

    # Write method metadata so merge_tests_by_class.py can resolve class/package reliably
    (method_out_dir / "method_meta.json").write_text(
        json.dumps({"methodId": method_id}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    repaired = False

    if validate_cmd_tpl:
        for wf in written:
            ok, output = run_validation(validate_cmd_tpl, wf, method_id)
            if ok:
                continue

            # One-round repair only.
            repaired = True
            repair_payload = dict(payload)
            repair_payload["unitTest"] = wf.read_text(encoding="utf-8")
            repair_payload["errorMessage"] = output

            try:
                repaired_resp = post_json(endpoint, repair_payload, timeout_sec)
            except (error.URLError, TimeoutError, json.JSONDecodeError) as exc:
                return {
                    "methodId": method_id,
                    "success": False,
                    "message": f"repair request failed: {exc}",
                    "writtenFiles": [str(p) for p in written],
                    "repaired": repaired,
                    "samplesCollected": len(collected_java),
                }

            if not bool(repaired_resp.get("success", False)):
                return {
                    "methodId": method_id,
                    "success": False,
                    "message": f"repair rejected: {repaired_resp.get('message', '')}",
                    "writtenFiles": [str(p) for p in written],
                    "repaired": repaired,
                    "samplesCollected": len(collected_java),
                }

            repair_files = repaired_resp.get("files") or []
            repair_written = write_files(method_out_dir, repair_files)
            if repair_written:
                written = repair_written

    return {
        "methodId": method_id,
        "success": True,
        "message": f"ok ({len(collected_java)} samples)",
        "writtenFiles": [str(p) for p in written],
        "repaired": repaired,
        "samplesCollected": len(collected_java),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Runtime2Test method-level scheduler")
    parser.add_argument("--endpoint", required=True, help="LLM server endpoint, e.g. http://127.0.0.1:1234/generation")
    parser.add_argument("--data-path", required=True, help="Path containing static-snapshot.json and invocations.json")
    parser.add_argument("--output-dir", required=True, help="Where generated files and manifest are written")
    parser.add_argument("--project-path", required=True, help="Target project path passed to server payload")
    parser.add_argument("--mode", default="HYBRID_DYNAMIC", choices=["HYBRID_DYNAMIC", "LLM_FIRST_STATIC"], help="Generation mode")
    parser.add_argument("--assertion-style", default="ASSERT_J_DEEP")
    parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--top-k", type=int, default=0, help="Only process top-k methods by invocation count; 0 means all")
    parser.add_argument("--method-filter", default="", help="Only process methods containing this substring")
    parser.add_argument("--method-list", default="", help="Path to a text file with one method_id per line; overrides --method-filter and --top-k")
    parser.add_argument("--max-facts-per-method", type=int, default=20)
    parser.add_argument("--related-limit", type=int, default=12, help="How many related methods to keep in static subset")
    parser.add_argument("--timeout-sec", type=int, default=120)
    parser.add_argument(
        "--validate-cmd",
        default="",
        help=(
            "Optional validator command template. "
            "Placeholders: {test_file}, {method_id}, {test_dir}. "
            "Non-zero exit triggers one repair round."
        ),
    )
    parser.add_argument("--manifest", default="manifest.json", help="Manifest file name under output-dir")
    parser.add_argument(
        "--samples-per-method",
        type=int,
        default=1,
        help="Number of independent samples to generate per method (results are merged/deduplicated). Default: 1",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    data_path = Path(args.data_path)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    static_path = data_path / "static-snapshot.json"
    invocations_path = data_path / "invocations.json"

    if not static_path.exists():
        raise FileNotFoundError(f"Missing {static_path}")
    if not invocations_path.exists():
        raise FileNotFoundError(f"Missing {invocations_path}")

    snapshot = _read_json(static_path)
    methods = snapshot.get("methods", [])
    method_map = {m.get("methodId"): m for m in methods if m.get("methodId")}

    per_method_facts, total_invocations = load_invocation_facts(invocations_path, args.max_facts_per_method)

    method_ids = list(method_map.keys())

    if args.method_list:
        allowed = set(Path(args.method_list).read_text(encoding="utf-8").splitlines())
        allowed = {m.strip() for m in allowed if m.strip()}
        method_ids = [mid for mid in method_ids if mid in allowed]
    else:
        if args.method_filter:
            method_ids = [mid for mid in method_ids if args.method_filter in mid]
        method_ids.sort(key=lambda mid: per_method_facts.get(mid, MethodFacts()).invocation_count, reverse=True)
        if args.top_k > 0:
            method_ids = method_ids[: args.top_k]

    print(f"Methods to process: {len(method_ids)}")

    results: list[dict[str, Any]] = []
    lock = threading.Lock()

    with ThreadPoolExecutor(max_workers=max(1, args.concurrency)) as pool:
        futures = [
            pool.submit(
                process_one_method,
                args.endpoint,
                mid,
                snapshot,
                method_map,
                per_method_facts,
                total_invocations,
                output_dir,
                args.project_path,
                args.mode,
                args.assertion_style,
                args.max_facts_per_method,
                False,
                args.related_limit,
                args.timeout_sec,
                args.validate_cmd or None,
                args.samples_per_method,
            )
            for mid in method_ids
        ]

        for f in as_completed(futures):
            res = f.result()
            with lock:
                results.append(res)
            status = "OK" if res.get("success") else "FAIL"
            print(f"[{status}] {res.get('methodId')} :: {res.get('message')}")

    manifest = {
        "endpoint": args.endpoint,
        "mode": args.mode,
        "projectPath": args.project_path,
        "dataPath": str(data_path),
        "methodsRequested": len(method_ids),
        "methodsSucceeded": sum(1 for r in results if r.get("success")),
        "methodsFailed": sum(1 for r in results if not r.get("success")),
        "results": sorted(results, key=lambda r: r.get("methodId", "")),
    }

    manifest_path = output_dir / args.manifest
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Manifest written to: {manifest_path}")

    return 0 if manifest["methodsFailed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
