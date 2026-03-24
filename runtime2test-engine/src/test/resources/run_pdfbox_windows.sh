#!/usr/bin/env bash

set -euo pipefail

trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

if ! command -v java >/dev/null 2>&1; then
    echo "Error: java not found in PATH. Please install JDK 17+ or set PATH correctly."
    exit 1
fi

JAR_PATH="$(ls -1t app/target/pdfbox-app-*.jar 2>/dev/null | head -n 1 || true)"
if [ -z "${JAR_PATH}" ]; then
    echo "Error: no jar found at app/target/pdfbox-app-*.jar"
    echo "Run this script from your pdfbox project root (e.g. C:/Software Engineering/pdfbox)."
    exit 1
fi

INPUT_PDF="CodeMonkey.pdf"
if [ ! -f "${INPUT_PDF}" ]; then
    INPUT_PDF="$(find . -maxdepth 1 -type f -name '*.pdf' ! -name 'Merged.pdf' | head -n 1 | sed 's#^./##')"
fi

if [ -z "${INPUT_PDF}" ] || [ ! -f "${INPUT_PDF}" ]; then
    echo "Error: no input PDF found in current directory: $(pwd)"
    echo "Place a PDF in this directory, or rename your file to CodeMonkey.pdf."
    exit 1
fi

run() {
    local extra_jvm_arg="${1:-}"

    if [ -n "${extra_jvm_arg}" ]; then
        java \
            -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
            "${extra_jvm_arg}" \
            -jar "${JAR_PATH}" "${@:2}"
    else
        java \
            -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
            -jar "${JAR_PATH}" "${@:2}"
    fi
}

rm -f CodeMonkey-*.pdf CodeMonkey*.jpg Merged.pdf

echo "Running with: ${JAR_PATH}"
echo "Input PDF: ${INPUT_PDF}"
#run "${1:-}" export:text --input=CodeMonkey.pdf --output=/dev/null
#run "${1:-}" split -split=1 --input=CodeMonkey.pdf
#run "${1:-}" merge -i $(find -iname 'CodeMonkey-*.pdf' | cat | tr '\n' ' ' | sed -E 's/ (\S)/ -i \1/g') --output=Merged.pdf
run "${1:-}" render --input="${INPUT_PDF}"
echo "Done"