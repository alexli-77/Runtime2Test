<div align="center">
  <h1>Runtime2Test</h1>
</div>

`Runtime2Test` is a tool that automatically converts real-world Java object behaviors during runtime into replayable test code. We utilize a combined approach of ProDJ + LLM. The objective is to generate test cases that are grounded in production-level workloads and realistic scenarios.

Reference: [Serializing Java Objects in Plain Code](http://arxiv.org/pdf/2405.11294) (Julian Wachter, Deepika Tiwari, Martin Monperrus and Benoit Baudry), Journal of Software and Systems, 2025.

```bibtex
@article{2405.11294,
 title = {Serializing Java Objects in Plain Code},
 journal = {Journal of Systems and Software},
 year = {2025},
 doi = {10.1016/j.jss.2025.112721},
 author = {Julian Wachter and Deepika Tiwari and Martin Monperrus and Benoit Baudry},
 url = {http://arxiv.org/pdf/2405.11294},
}
```

## Setup
The easiest way to get an executable version of `Runtime2Test` is to use the provided
`flake.nix`:
1. Enter a dev-shell using `nix develop`
2. Run `java -jar runtime2test-engine/target/runtime2test-engine.jar --statistics <config file>`.
   You can find example config files in `runtime2test-engine/src/test/resources/`.

_____________________________________________

Updates on 20260303

Environment:

Maven : Apache Maven 3.9.12 

Java : openjdk version "17.0.18"

Windows X64

##How to run this project?

Step 1:

Compile and package : mvn -DskipTests package

Step 2:

Put the Runtime2Test\runtime2test-engine\src\test\resources\CodeMonkey.pdf to the root path of the Pdfbox

Step 3:

git clone https://github.com/apache/pdfbox.git -b trunk

Compile and package Pdfbox.

Step 4:

For windows: java -jar runtime2test-engine/target/runtime2test-engine.jar --statistics runtime2test-engine/src/test/resources/pdfbox_windows.json

For Mac: ...

For Linux: ...

## What will happen?

Data and new tests will be generated in the Pdfbox. 

## How the run the generated tests?

Set-Location 'C:\your_path\pdfbox'; mvn -Dtest=*RockyTest -Dsurefire.failIfNoSpecifiedTests=false test

![Capture](runtime2test-engine/src/test/resources/Capture.png)

## About the LLM

I'm working on integrating CodeT5-base to this pipeline. CodeT5-base is a Transformer-based pre-trained model for programming languages, proposed by researchers at Salesforce.It is built on the architecture of T5 and is designed specifically for code understanding and code generation tasks involving both natural language (NL) and source code.

Reference：[Wang, Yue, et al. "Codet5: Identifier-aware unified pre-trained encoder-decoder models for code understanding and generation." ](2021.https://aclanthology.org/2021.emnlp-main.685.pdf)

Github: [chatunitest-models](https://colab.research.google.com/drive/1vxEPDWy57nkjCusNUEQuzfav5OFzdBNz?usp=sharing)


## Hybrid-Dynamic Configuration 

Runtime2Test now supports three generation modes:

1. `RULE_ONLY`: Generate tests from recorded runtime invocations only.
2. `LLM_FIRST_STATIC`: Try LLM generation from static snapshot first, then fall back to `RULE_ONLY`.
3. `HYBRID_DYNAMIC`: Send both static snapshot and optional runtime facts to the LLM, then fall back to `RULE_ONLY` if needed.

### Recommended config files

Use these two example configs under `runtime2test-engine/src/test/resources/`:

1. `pdfbox_windows_hybrid_course.json`
   - Intended for coursework/stable runs.
   - `hybridEnableRuntimeFacts` is set to `false`.

2. `pdfbox_windows_hybrid.json`
   - Intended for research/behavior exploration.
   - `hybridEnableRuntimeFacts` is set to `true`.

### Key HYBRID_DYNAMIC fields

1. `generationMode`: Must be `HYBRID_DYNAMIC`.
2. `llmEndpoint`: Full remote endpoint URL. Use a full path such as `https://<ngrok-domain>/generation`.
3. `hybridEnableRuntimeFacts`:
   - `true`: Build and send runtime facts.
   - `false`: Skip runtime facts and send an empty `runtimeFacts` payload.
4. `hybridMaxMethods`: Max number of methods to keep in runtime facts summary.
5. `hybridMaxFactsPerMethod`: Max number of fact entries retained per method.
6. `hybridIncludeRawEvents`: Forward a hint flag to the model service for raw-event handling.

### Expected response contract from model service

The Java client expects the response JSON in this shape:

```json
{
  "success": true,
  "message": "ok",
  "files": [
    {
      "relativePath": "se/example/GeneratedRockyTest.java",
      "content": "...java source..."
    }
  ]
}
```

Reference files in this repository:

1. Contract document: `runtime2test-engine/src/test/resources/LLM/RUNTIME2TEST_SERVER_CONTRACT.md`
2. Flask adapter example: `runtime2test-engine/src/test/resources/LLM/runtime2test_adapter_example.py`
3. Package migration blueprint: `MIGRATION_BATCH4_PACKAGE_RESTRUCTURE_PLAN.md`

### Run command example

```bash
java -jar runtime2test-engine/target/runtime2test-engine.jar --statistics runtime2test-engine/src/test/resources/pdfbox_windows_hybrid.json
```

For coursework mode, replace the config with:

```bash
java -jar runtime2test-engine/target/runtime2test-engine.jar --statistics runtime2test-engine/src/test/resources/pdfbox_windows_hybrid_course.json
```


