package org.threeTesters.runtime2test.engine.instrument;

import org.threeTesters.runtime2test.engine.serialization.JavaSnippet;

public interface RecordedTargetedInvocation {

  int targetId();

  RecordedMethod recordedMethod();

  JavaSnippet returned();
}
