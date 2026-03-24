package org.threeTesters.runtime2test.construction.construction.actions;

import java.util.Collection;
import org.threeTesters.runtime2test.construction.construction.solving.Costs;
import spoon.reflect.declaration.CtField;

public record ActionMockObject(Collection<CtField<?>> handledFields) implements Action {

  @Override
  public boolean constructsInstance() {
    return true;
  }

  @Override
  public boolean needsInstance() {
    return false;
  }

  @Override
  public int cost() {
    return Costs.MOCK_OBJECT;
  }

  @Override
  public String toString() {
    return """
        ActionUseEnumConstant{
        }""";
  }
}
