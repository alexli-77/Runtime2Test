package org.threeTesters.runtime2test.engine.instrument.aspects;

import org.threeTesters.runtime2test.engine.instrument.CaptureContextHolder;
import org.threeTesters.runtime2test.engine.instrument.MutationTracingContextHolder;

public class MutationTracingSubstitutionPointcut {

  public static void onFieldWrite(
      Object receiver,
      Object newValue,
      Object oldValue
  ) {
    if (CaptureContextHolder.isInAgentCode()) {
      return;
    }

    try (var ignored = CaptureContextHolder.enterAgentCode()) {
      MutationTracingContextHolder.registerSetField(
          receiver,
          newValue,
          oldValue
      );
    } catch (Exception e) {
      e.printStackTrace();
      Runtime.getRuntime().halt(6);
      throw e;
    }
  }

  public static void onMutatorCalled(Object receiver) {
    if (CaptureContextHolder.isInAgentCode()) {
      return;
    }

    try (var ignored = CaptureContextHolder.enterAgentCode()) {
      MutationTracingContextHolder.registerCallMutator(receiver);
    } catch (Exception e) {
      e.printStackTrace();
      Runtime.getRuntime().halt(7);
      throw e;
    }
  }

}
