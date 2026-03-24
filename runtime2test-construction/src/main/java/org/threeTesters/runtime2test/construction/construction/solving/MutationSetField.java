package org.threeTesters.runtime2test.construction.construction.solving;

import static org.threeTesters.runtime2test.construction.util.SpoonUtil.isAccessible;

import org.threeTesters.runtime2test.construction.construction.actions.ActionSetField;
import spoon.reflect.declaration.CtField;

public class MutationSetField implements MutationStrategy {

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public Result register(SolvingState state, Object instance) {
    SolvingState current = state;
    boolean foundAction = false;

    for (CtField<?> field : state.type().getFields()) {
      if (!isAccessible(field) || field.isFinal() || field.isStatic()) {
        continue;
      }

      current = current.withAction(new ActionSetField(field));
      foundAction = true;
    }

    return foundAction ? Result.successStatic(current) : Result.failedStatic();
  }
}
