#!/usr/bin/env bash

trap 'trap - SIGTERM && kill -- -$$' SIGINT SIGTERM EXIT

run() {
    local java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
    local app_jar
    app_jar=$(ls app/target/pdfbox-app-*.jar 2>/dev/null | head -n 1)

    if [[ -z "$app_jar" ]]; then
        echo "Could not find app/target/pdfbox-app-*.jar. Build pdfbox first." >&2
        return 1
    fi

    "$java_bin" \
        `#'-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005'` \
        -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints \
        "$1" \
        -jar "$app_jar" "${@:2}"
}

rm -f CodeMonkey-*.pdf CodeMonkey*.jpg Merged.pdf

echo "Running"
#run "$1" export:text --input=CodeMonkey.pdf --output=/dev/null
#run "$1" split -split=1 --input=CodeMonkey.pdf
#run "$1" merge -i $(find -iname 'CodeMonkey-*.pdf' | cat | tr '\n' ' ' | sed -E 's/ (\S)/ -i \1/g') --output=Merged.pdf
run "$1" render --input=CodeMonkey.pdf
echo "Done"
