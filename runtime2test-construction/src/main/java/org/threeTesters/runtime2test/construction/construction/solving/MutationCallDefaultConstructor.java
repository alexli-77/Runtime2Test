package org.threeTesters.runtime2test.construction.construction.solving;

import static org.threeTesters.runtime2test.construction.util.SpoonUtil.isAccessible;

import org.threeTesters.runtime2test.construction.construction.actions.ActionCallConstructor;
import org.threeTesters.runtime2test.construction.util.ClassUtil.ConstructorMapping;
import spoon.reflect.declaration.CtConstructor;

public class MutationCallDefaultConstructor implements MutationStrategy {

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public Result register(SolvingState state, Object instance) {
    CtConstructor<?> defaultConstructor = state.type().getConstructor();
    if (defaultConstructor == null || !isAccessible(defaultConstructor)) {
      return Result.failedStatic();
    }

    return Result.successStatic(state.withAction(
        new ActionCallConstructor(
            defaultConstructor,
            new ConstructorMapping(defaultConstructor)
        )
    ));
  }
}
