package datadog.trace.instrumentation.axway;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class HTTPPluginAdvice {

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(
      @Advice.Enter final AgentScope scope,
      @Advice.Argument(value = 3) final Object serverTransaction,
      @Advice.This final Object httpPlugin,
      @Advice.Thrown final Throwable error) {
    try {
      // ServerTransaction extends HTTPTransaction :
      Method m = serverTransaction.getClass().getDeclaredMethod("getHeaders"); // native method
      m.setAccessible(true);
      Object headers = m.invoke(serverTransaction);
      // System.err.println("headers: " + headers);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
    }
    if (scope != null) {
      scope.close();
    }
  }
}
