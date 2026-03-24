package org.threeTesters.runtime2test.construction.construction.solving;

import org.threeTesters.runtime2test.construction.construction.actions.ActionCallConstructor;
import org.threeTesters.runtime2test.construction.util.ClassUtil;
import org.threeTesters.runtime2test.construction.util.SpoonUtil;

public class MutationBindConstructorParameter implements MutationStrategy {

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public Result register(SolvingState state, Object instance) {
    var constructors = ClassUtil.getConstructorFieldAssignments(
        state.type(),
        SpoonUtil::isAccessible
    );

    if (constructors.isEmpty()) {
      return Result.failedStatic();
    }

    SolvingState current = state;
    for (var entry : constructors.entrySet()) {
      current = current.withAction(new ActionCallConstructor(entry.getKey(), entry.getValue()));
    }

    return Result.successStatic(current);
  }

}
