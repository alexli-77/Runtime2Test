package org.threeTesters.runtime2test.construction.construction.solving;

import org.threeTesters.runtime2test.construction.construction.actions.ActionUseEnumConstant;
import spoon.reflect.declaration.CtEnum;

public class MutationUseEnumConstant implements MutationStrategy {

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public Result register(SolvingState state, Object instance) {
    if (!(state.type() instanceof CtEnum<?> ctEnum)) {
      return Result.failedStatic();
    }
    return Result.successStatic(state.withAction(new ActionUseEnumConstant(ctEnum)));
  }
}
