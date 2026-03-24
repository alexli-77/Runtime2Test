package org.threeTesters.runtime2test.engine.instrument;

import org.threeTesters.runtime2test.engine.serialization.JavaSnippet;
import java.util.UUID;

public record RecordedMockedInvocation(
    UUID parentInvocationId,
    RecordedMethod recordedMethod,
    JavaSnippet returned,
    int targetId
) implements RecordedTargetedInvocation {

}
