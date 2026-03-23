package se.kth.castor.rockstofetch.staticdata;

import java.util.List;

public record StaticMethodSnapshot(
    String methodId,
    String declaringType,
    String methodName,
    String returnType,
    boolean returnsVoid,
    List<String> parameterNames,
    List<String> parameterTypes,
    List<String> nestedInvocationTargets,
    List<String> directCallersInType,
    List<String> directCalleesInType,
    boolean reachableFromTestInType,
    String sourceSnippet
) {

  public StaticMethodSnapshot {
    parameterNames = List.copyOf(parameterNames);
    parameterTypes = List.copyOf(parameterTypes);
    nestedInvocationTargets = List.copyOf(nestedInvocationTargets);
    directCallersInType = List.copyOf(directCallersInType);
    directCalleesInType = List.copyOf(directCalleesInType);
  }
}
