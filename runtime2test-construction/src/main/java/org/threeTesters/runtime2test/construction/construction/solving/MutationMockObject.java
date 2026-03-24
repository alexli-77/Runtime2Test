package org.threeTesters.runtime2test.construction.construction.solving;

import java.util.Set;
import org.threeTesters.runtime2test.construction.construction.actions.ActionMockObject;

public class MutationMockObject implements MutationStrategy {

  private final Set<String> allowedTypes;

  public MutationMockObject(Set<String> allowedTypes) {
    this.allowedTypes = Set.copyOf(allowedTypes);
  }

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public Result register(SolvingState state, Object instance) {
    if (!allowedTypes.contains(state.type().getQualifiedName())) {
      return Result.failedStatic();
    }
    return Result.successStatic(state.withAction(new ActionMockObject(state.fields())));
  }
}
