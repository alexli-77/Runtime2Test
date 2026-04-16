#!/bin/bash
# Run LLM test generation for all 79 ProDJ-aligned methods.
# Upload this script and prodj_aligned_methods.txt to Lightning CPU Studio,
# then run: bash run_prodj_aligned.sh <tunnel-url> [samples] [mode]
#
# Usage:
#   bash run_prodj_aligned.sh https://xxxx.trycloudflare.com            # runtime-grounded
#   bash run_prodj_aligned.sh https://xxxx.trycloudflare.com 3          # 3 samples, runtime-grounded
#   bash run_prodj_aligned.sh https://xxxx.trycloudflare.com 1 LLM_FIRST_STATIC  # static-only

ENDPOINT="${1}/generation"
SAMPLES="${2:-1}"                      # default: 1 sample per method
MODE="${3:-HYBRID_DYNAMIC}"            # default: runtime-grounded
DATA_PATH="/teamspace/studios/this_studio/pdfbox_dataset"
OUTPUT_DIR="/teamspace/studios/this_studio/r2t-output-prodj-aligned-${MODE}-s${SAMPLES}"
PROJECT_PATH="/teamspace/studios/this_studio/pdfbox"
METHODS_FILE="/teamspace/studios/this_studio/Runtime2Test/runtime2test-engine/src/test/resources/prodj_aligned_methods.txt"
SCHEDULER="/teamspace/studios/this_studio/Runtime2Test/runtime2test-engine/src/test/resources/LLM/method_level_scheduler.py"

if [ -z "$1" ]; then
    echo "Usage: bash run_prodj_aligned.sh <tunnel-url> [samples-per-method] [mode]"
    echo "  mode: HYBRID_DYNAMIC (default) | LLM_FIRST_STATIC"
    echo "Example (runtime-grounded): bash run_prodj_aligned.sh https://xxxx.trycloudflare.com"
    echo "Example (static-only):      bash run_prodj_aligned.sh https://xxxx.trycloudflare.com 1 LLM_FIRST_STATIC"
    exit 1
fi

echo "Endpoint: $ENDPOINT"
echo "Mode: $MODE"
echo "Samples per method: $SAMPLES"
echo "Methods file: $METHODS_FILE"
echo "Output dir: $OUTPUT_DIR"
echo ""

# Read methods and build --method-filter patterns by unique class prefixes
# Run scheduler once per class to avoid filter limitations
declare -A class_methods
while IFS= read -r method_id; do
    # Extract class prefix: everything before #
    class_prefix="${method_id%%#*}"
    class_methods["$class_prefix"]=1
done < "$METHODS_FILE"

total_classes=${#class_methods[@]}
echo "Total classes to process: $total_classes"
echo ""

count=0
for class_prefix in "${!class_methods[@]}"; do
    count=$((count + 1))
    echo "[$count/$total_classes] Processing: $class_prefix"
    python3 "$SCHEDULER" \
        --endpoint "$ENDPOINT" \
        --data-path "$DATA_PATH" \
        --output-dir "$OUTPUT_DIR" \
        --project-path "$PROJECT_PATH" \
        --mode "$MODE" \
        --method-filter "$class_prefix" \
        --concurrency 1 \
        --timeout-sec 180 \
        --samples-per-method "$SAMPLES"
    echo ""
done

echo "=== All done ==="
echo "Results in: $OUTPUT_DIR"
