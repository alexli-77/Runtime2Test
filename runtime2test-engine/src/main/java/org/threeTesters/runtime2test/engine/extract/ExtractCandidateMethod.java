package org.threeTesters.runtime2test.engine.extract;

import spoon.reflect.declaration.CtMethod;

public record ExtractCandidateMethod(
    CtMethod<?> spoonMethod,
    RecordingCandidateMethod recordingCandidate
) {
}
