# Batch 4 Plan: Package and Structure Migration (Execution Blueprint)

This batch provides a concrete migration blueprint only. It does **not** rename packages yet.

## Goal

Prepare a safe, reviewable plan to migrate package namespace and related file structure from legacy namespaces to a new project namespace, while preserving license and attribution.

## Scope Summary

- Java source files with legacy package prefixes: **101 files**
  - `runtime2test-construction/src/main/java/**`
  - `runtime2test-engine/src/main/java/**`
- Build metadata and entrypoints referencing old namespace:
  - `pom.xml`
  - `runtime2test-construction/pom.xml`
  - `runtime2test-engine/pom.xml`
- Runtime/config/documentation references to review and update as needed:
  - `README.md`
  - `runtime2test-engine/src/test/resources/**/*.json`
  - `runtime2test-engine/src/test/resources/**/*.sh`

## Preconditions (Required Before Actual Rename)

1. Pick target namespace, for example:
   - `org.yourorg.runtime2test`
2. Confirm whether module names stay unchanged:
   - `runtime2test-construction`
   - `runtime2test-engine`
3. Freeze branch and ensure compile baseline is green:
   - `mvn -pl runtime2test-engine -DskipTests compile`

## Migration Strategy

Use a staged approach with compile checkpoints after each stage.

### Stage 1: Namespace mapping definition

Final mapping table (approved and executed):

- `org.threeTesters.runtime2test.engine` for the former engine package tree
- `org.threeTesters.runtime2test.construction` for the former construction package tree
- `org.threeTesters.runtime2test` as the shared root namespace

Deliverable:
- A reviewed mapping document committed before code edits.

### Stage 2: Build metadata and entrypoint update

Update these first so startup classes point to the new package names:

1. `pom.xml` (`groupId`)
2. `runtime2test-construction/pom.xml` (`groupId`)
3. `runtime2test-engine/pom.xml`
   - `groupId`
   - `Main-Class`
   - `Premain-Class`

Checkpoint:
- Maven can still resolve module graph.

### Stage 3: Java package + imports migration

Apply package rename across Java files in two passes:

1. Pass A: `runtime2test-engine` tree
2. Pass B: `runtime2test-construction` tree

Rules:
- Rename `package` declarations first.
- Then rename imports and fully-qualified references.
- Avoid behavior changes in this stage.

Checkpoint after each pass:
- `mvn -pl runtime2test-engine -DskipTests compile`
- `mvn -pl runtime2test-construction -DskipTests compile`

### Stage 4: Config/resource/script alignment

Review and update references that may still contain old namespace/class names:

- JSON configs in `runtime2test-engine/src/test/resources/`
- shell/cmd scripts in `runtime2test-engine/src/test/resources/`
- docs in `README.md`

Checkpoint:
- Example commands in docs point to valid class and config paths.

### Stage 5: Integration compile and smoke checks

Run integration compile at root:

- `mvn -DskipTests compile`

Then run one smoke command for generation (no remote connectivity validation in this batch):

- local generation command using an existing config file

## Risk Areas

1. ByteBuddy entrypoints in manifest (`Main-Class`, `Premain-Class`) can silently fail if not updated.
2. Reflection-based references may remain as string literals and are not always caught by compiler.
3. Cross-module imports (`runtime2test-engine` <-> `runtime2test-construction`) are high-risk during partial renames.

## Rollback Plan

Use commit boundaries per stage:

1. Commit after Stage 2
2. Commit after Stage 3 Pass A
3. Commit after Stage 3 Pass B
4. Commit after Stage 4

If a stage fails, revert only to the previous stage commit.

## Acceptance Criteria for Starting Actual Rename (Next Batch)

1. Target namespace is approved.
2. Mapping table is approved.
3. Stage-by-stage order is approved.
4. Compile checkpoints are approved.

When all four are approved, next batch can execute real package and path modifications.
