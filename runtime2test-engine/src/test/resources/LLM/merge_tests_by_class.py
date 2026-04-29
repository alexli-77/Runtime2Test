#!/usr/bin/env python3
"""
Merge LLM-generated per-method test files into per-class test files,
matching ProDJ's output structure (XxxRockyTest.java per class).

Parses class/package from the output directory name, which has the format:
  org.apache.pdfbox.cos.COSArray_getObject_int___<hash>
  └─── fully-qualified class name is everything before the first segment
       that starts with a lowercase letter following an underscore AND
       does NOT contain a dot (i.e. the method name part).

Usage:
    python3 merge_tests_by_class.py \
        --input-dir /path/to/r2t-output-prodj-aligned-HYBRID_DYNAMIC-s1 \
        --output-dir /path/to/merged-tests
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


COMMON_IMPORTS = (
    "import org.junit.jupiter.api.Test;\n"
    "import static org.junit.jupiter.api.Assertions.*;"
)


def parse_method_id(method_id: str) -> tuple[str, str] | None:
    """
    Extract (package, simple_class_name) from a methodId string.

    Example:
      "org.apache.pdfbox.cos.COSArray#getObject(int)"
      → ("org.apache.pdfbox.cos", "COSArray")
    """
    fqn = method_id.split('#')[0]          # "org.apache.pdfbox.cos.COSArray"
    parts = fqn.rsplit('.', 1)
    if len(parts) != 2:
        return None
    package, class_name = parts
    if not package or not class_name:
        return None
    return package, class_name


def extract_clean_test_methods(content: str) -> list[str]:
    """
    Extract individual @Test method blocks using brace-depth counting.
    Discards trailing garbage (static final fields, @BeforeEach stubs, etc.).
    """
    first = content.find("@Test")
    if first == -1:
        return []
    body = content[first:]
    parts = re.split(r'(?=@Test)', body)
    methods = []
    for part in parts:
        part = part.strip()
        if not part.startswith("@Test") or "{" not in part:
            continue
        # Balance braces to find exact method end
        depth = 0
        end = -1
        in_str = False
        for i, ch in enumerate(part):
            if ch == '"' and (i == 0 or part[i - 1] != '\\'):
                in_str = not in_str
            if in_str:
                continue
            if ch == '{':
                depth += 1
            elif ch == '}':
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        methods.append(part[:end] if end != -1 else part)
    return methods


def merge(input_dir: Path, output_dir: Path) -> None:
    # Group method directories by (package, class_name)
    class_map: dict[tuple[str, str], list[Path]] = {}

    for d in sorted(input_dir.iterdir()):
        if not d.is_dir():
            continue

        # Prefer method_meta.json written by scheduler (exact methodId)
        meta_file = d / "method_meta.json"
        parsed = None
        if meta_file.exists():
            try:
                meta = json.loads(meta_file.read_text(encoding="utf-8"))
                parsed = parse_method_id(meta.get("methodId", ""))
            except Exception:
                pass

        if parsed is None:
            print(f"[SKIP] no method_meta.json and cannot parse dirname: {d.name}")
            continue

        package, class_name = parsed
        java_files = list(d.rglob("*.java"))
        if not java_files:
            continue
        key = (package, class_name)
        class_map.setdefault(key, []).extend(java_files)

    output_dir.mkdir(parents=True, exist_ok=True)
    total_classes = 0
    total_tests = 0

    for (package, class_name), java_files in sorted(class_map.items()):
        all_methods: list[str] = []
        for jf in java_files:
            content = jf.read_text(encoding="utf-8", errors="ignore")
            all_methods.extend(extract_clean_test_methods(content))

        if not all_methods:
            continue

        # Deduplicate by method name
        seen: set[str] = set()
        unique: list[str] = []
        for m in all_methods:
            nm = re.search(r'void\s+(\w+)\s*\(', m)
            name = nm.group(1) if nm else None
            if name and name in seen:
                continue
            if name:
                seen.add(name)
            unique.append(m)

        test_class = f"{class_name}RockyTest"
        indented = "\n\n    ".join(m.replace("\n", "\n    ") for m in unique)
        java_src = (
            f"package {package};\n\n"
            f"{COMMON_IMPORTS}\n\n"
            f"public class {test_class} {{\n\n"
            f"    {indented}\n\n"
            f"}}\n"
        )

        pkg_path = output_dir / Path(*package.split('.'))
        pkg_path.mkdir(parents=True, exist_ok=True)
        (pkg_path / f"{test_class}.java").write_text(java_src, encoding="utf-8")
        print(f"  [{package}] {test_class:<50} {len(unique):3} tests")
        total_classes += 1
        total_tests += len(unique)

    print(f"\n=== Done ===")
    print(f"Classes:     {total_classes}")
    print(f"@Test total: {total_tests}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Merge per-method LLM tests into per-class files")
    parser.add_argument("--input-dir", required=True, help="r2t-output-* directory")
    parser.add_argument("--output-dir", required=True, help="Destination for merged per-class files")
    args = parser.parse_args()
    merge(Path(args.input_dir), Path(args.output_dir))


if __name__ == "__main__":
    main()
