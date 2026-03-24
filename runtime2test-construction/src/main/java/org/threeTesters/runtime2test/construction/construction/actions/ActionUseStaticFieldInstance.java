package org.threeTesters.runtime2test.construction.construction.actions;

import java.util.Collection;
import org.threeTesters.runtime2test.construction.construction.solving.Costs;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;

public record ActionUseStaticFieldInstance(
    CtType<?> type,
    Collection<CtField<?>> handledFields,
    CtField<?> field
) implements Action {

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
    return Costs.USE_STATIC_FIELD;
  }

  @Override
  public String toString() {
    return """
        ActionUseStaticFieldInstance{
         ## FIELD
           %s
        }""".formatted(field);
  }
}
