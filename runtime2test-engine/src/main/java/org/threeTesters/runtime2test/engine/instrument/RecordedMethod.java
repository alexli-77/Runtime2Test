package org.threeTesters.runtime2test.engine.instrument;

import static org.threeTesters.runtime2test.engine.util.Classes.className;

import org.threeTesters.runtime2test.engine.util.Classes;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public record RecordedMethod(
    String declaringClassName,
    String methodName,
    List<String> parameterTypes
) {

  public static RecordedMethod fromReflectMethod(Method method) {
    return new RecordedMethod(
        method.getDeclaringClass().getName(),
        method.getName(),
        Arrays.stream(method.getParameters()).map(it -> Classes.className(it.getType())).toList()
    );
  }

  public static RecordedMethod fromType(String declaringClass, String name, MethodTypeDesc type) {
    return new RecordedMethod(
        declaringClass,
        name,
        type.parameterList().stream().map(Classes::className).toList()
    );
  }

  public String fqnWithSignature() {
    return declaringClassName() + "#" + signature();
  }

  public String signature() {
    return methodName() + "(" + String.join(",", parameterTypes()) + ")";
  }

}
