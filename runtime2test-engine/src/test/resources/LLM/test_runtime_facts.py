#!/usr/bin/env python3
"""Quick test to verify runtimeFacts now includes real returned values and parameter values."""

import json
import sys
from pathlib import Path

# Import from scheduler
sys.path.insert(0, str(Path(__file__).parent))
from method_level_scheduler import load_invocation_facts, build_runtime_facts_payload

DATA_PATH = Path("/teamspace/studios/this_studio/pdfbox_dataset")


def main():
    invocations_path = DATA_PATH / "invocations.json"

    print("Loading invocation facts...")
    per_method, total = load_invocation_facts(invocations_path, max_shapes=5)
    print(f"Total invocation lines: {total}")
    print(f"Unique methods: {len(per_method)}\n")

    # Check first 3 methods
    for method_id, facts in list(per_method.items())[:3]:
        print(f"Method: {method_id}")
        print(f"  invocation_count : {facts.invocation_count}")
        print(f"  returned_values  : {facts.returned_values}")
        print(f"  parameter_values : {facts.parameter_values}")

        payload = build_runtime_facts_payload(method_id, per_method, total)
        method_payload = payload["methods"][0]
        print(f"  payload returnedValues  : {method_payload['returnedValues']}")
        print(f"  payload parameterValues : {method_payload['parameterValues']}")

        # Check
        if method_payload["returnedValues"]:
            print(f"  ✅ returnedValues present")
        else:
            print(f"  ❌ returnedValues EMPTY")
        print()


if __name__ == "__main__":
    main()
