package org.threeTesters.runtime2test.engine.staticdata;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record StaticProjectSnapshot(
    int schemaVersion,
    Instant generatedAt,
    Path projectPath,
    int candidateCount,
    List<StaticMethodSnapshot> methods
) {

  public StaticProjectSnapshot {
    methods = List.copyOf(methods);
  }
}
