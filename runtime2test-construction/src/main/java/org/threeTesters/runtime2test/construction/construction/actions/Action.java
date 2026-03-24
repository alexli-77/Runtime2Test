package org.threeTesters.runtime2test.construction.construction.actions;

import java.util.Collection;
import spoon.reflect.declaration.CtField;

public sealed interface Action permits ActionCallConstructor, ActionCallFactoryMethod,
    ActionCallSetter, ActionFixmeConstructObject, ActionMockObject, ActionObjectReference, ActionSetField,
    ActionUseEnumConstant, ActionUseStaticFieldInstance {

  Collection<CtField<?>> handledFields();

  boolean constructsInstance();

  boolean needsInstance();

  int cost();
}
