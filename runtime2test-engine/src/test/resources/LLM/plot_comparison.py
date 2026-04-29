#!/usr/bin/env python3
"""
Generate comparison charts across three test generation conditions:
  - RULE_ONLY
  - LLM_FIRST_STATIC
  - HYBRID_DYNAMIC

Charts produced:
  1. Test volume (classes + @Test methods)
  2. Assertion coverage rate (% of @Test methods that contain at least one assertion)
  3. Test class distribution by Java package
"""

import re
from pathlib import Path
from collections import defaultdict

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

# ── Paths ────────────────────────────────────────────────────────────────────

SOURCES = {
    "RULE_ONLY": Path(
        "/Users/files/code/github/pdfbox/rtf/generated-tests/src/test/java"
    ),
    "LLM_FIRST_STATIC": Path(
        "/Users/files/code/github/Runtime2Test/runtime2test-engine"
        "/src/test/resources/LLM/merged-LLM_FIRST_STATIC"
    ),
    "HYBRID_DYNAMIC": Path(
        "/Users/files/code/github/Runtime2Test/runtime2test-engine"
        "/src/test/resources/LLM/merged-HYBRID_DYNAMIC"
    ),
}

OUTPUT_DIR = Path(__file__).parent / "charts"
OUTPUT_DIR.mkdir(exist_ok=True)

COLORS = {
    "RULE_ONLY":        "#4C72B0",
    "LLM_FIRST_STATIC": "#DD8452",
    "HYBRID_DYNAMIC":   "#55A868",
}

ASSERT_RE = re.compile(r'\b(assert\w+|fail\b)', re.IGNORECASE)

# ── Data collection ───────────────────────────────────────────────────────────

def collect(root: Path) -> dict:
    files = [f for f in root.rglob("*.java") if "RockyTest" in f.name]
    total_tests = 0
    has_assert = 0
    pkg_count: dict[str, int] = defaultdict(int)

    for f in files:
        content = f.read_text(errors="ignore")

        # package bucket (4-level)
        parts = f.relative_to(root).parts
        depth = min(4, len(parts) - 1)
        pkg = ".".join(parts[:depth])
        pkg_count[pkg] += 1

        # @Test blocks
        for block in re.split(r'(?=@Test)', content):
            if "@Test" not in block:
                continue
            total_tests += 1
            if ASSERT_RE.search(block):
                has_assert += 1

    return {
        "classes":    len(files),
        "tests":      total_tests,
        "has_assert": has_assert,
        "pkg_count":  dict(pkg_count),
    }


data = {name: collect(root) for name, root in SOURCES.items()}

# ── Chart 1: Test volume ──────────────────────────────────────────────────────

fig, ax = plt.subplots(figsize=(8, 5))

conditions = list(data.keys())
x = np.arange(len(conditions))
width = 0.35

classes = [data[c]["classes"] for c in conditions]
tests   = [data[c]["tests"]   for c in conditions]

bars1 = ax.bar(x - width / 2, classes, width, label="Test Classes",
               color=[COLORS[c] for c in conditions], alpha=0.85)
bars2 = ax.bar(x + width / 2, tests,   width, label="@Test Methods",
               color=[COLORS[c] for c in conditions], alpha=0.45,
               edgecolor=[COLORS[c] for c in conditions], linewidth=1.2)

for bar in bars1:
    ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 2,
            str(int(bar.get_height())), ha="center", va="bottom", fontsize=10)
for bar in bars2:
    ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 2,
            str(int(bar.get_height())), ha="center", va="bottom", fontsize=10)

ax.set_xticks(x)
ax.set_xticklabels(conditions, fontsize=11)
ax.set_ylabel("Count", fontsize=11)
ax.set_title("Test Generation Volume by Condition", fontsize=13, fontweight="bold")
ax.legend(fontsize=10)
ax.set_ylim(0, max(tests) * 1.18)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)

plt.tight_layout()
out1 = OUTPUT_DIR / "chart1_test_volume.png"
plt.savefig(out1, dpi=150)
plt.close()
print(f"Saved: {out1}")

# ── Chart 2: Assertion coverage rate ─────────────────────────────────────────

fig, ax = plt.subplots(figsize=(7, 4.5))

rates = [100 * data[c]["has_assert"] / data[c]["tests"] for c in conditions]

bars = ax.bar(conditions, rates,
              color=[COLORS[c] for c in conditions], alpha=0.85, width=0.45)

for bar, rate, cond in zip(bars, rates, conditions):
    raw = data[cond]["has_assert"]
    total = data[cond]["tests"]
    ax.text(bar.get_x() + bar.get_width() / 2,
            bar.get_height() + 0.4,
            f"{rate:.1f}%\n({raw}/{total})",
            ha="center", va="bottom", fontsize=10)

ax.set_ylabel("% of @Test methods with ≥1 assertion", fontsize=11)
ax.set_title("Assertion Coverage Rate by Condition", fontsize=13, fontweight="bold")
ax.set_ylim(90, 102)
ax.set_yticks(range(90, 103, 2))
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)

plt.tight_layout()
out2 = OUTPUT_DIR / "chart2_assertion_coverage.png"
plt.savefig(out2, dpi=150)
plt.close()
print(f"Saved: {out2}")

# ── Chart 3: Package distribution ────────────────────────────────────────────

# Collect all packages that appear in any condition
all_pkgs: set[str] = set()
for d in data.values():
    all_pkgs.update(d["pkg_count"].keys())

# Short labels
def short(pkg: str) -> str:
    parts = pkg.split(".")
    # keep last 2 meaningful segments
    if len(parts) >= 2:
        return ".".join(parts[-2:])
    return pkg

all_pkgs_sorted = sorted(all_pkgs, key=lambda p: (
    -max(data[c]["pkg_count"].get(p, 0) for c in conditions), p
))

short_labels = [short(p) for p in all_pkgs_sorted]
n_pkgs = len(all_pkgs_sorted)
x = np.arange(n_pkgs)
width = 0.25

fig, ax = plt.subplots(figsize=(max(10, n_pkgs * 1.1), 5))

for i, cond in enumerate(conditions):
    counts = [data[cond]["pkg_count"].get(p, 0) for p in all_pkgs_sorted]
    offset = (i - 1) * width
    bars = ax.bar(x + offset, counts, width,
                  label=cond, color=COLORS[cond], alpha=0.85)
    for bar, cnt in zip(bars, counts):
        if cnt > 0:
            ax.text(bar.get_x() + bar.get_width() / 2,
                    bar.get_height() + 0.1,
                    str(cnt), ha="center", va="bottom", fontsize=8)

ax.set_xticks(x)
ax.set_xticklabels(short_labels, rotation=30, ha="right", fontsize=9)
ax.set_ylabel("Test Classes", fontsize=11)
ax.set_title("Test Class Distribution by Java Package", fontsize=13, fontweight="bold")
ax.legend(fontsize=10)
ax.set_ylim(0, max(
    data[c]["pkg_count"].get(p, 0)
    for c in conditions for p in all_pkgs_sorted
) * 1.25)
ax.spines["top"].set_visible(False)
ax.spines["right"].set_visible(False)

plt.tight_layout()
out3 = OUTPUT_DIR / "chart3_package_distribution.png"
plt.savefig(out3, dpi=150)
plt.close()
print(f"Saved: {out3}")

print("\nAll charts saved to:", OUTPUT_DIR)
