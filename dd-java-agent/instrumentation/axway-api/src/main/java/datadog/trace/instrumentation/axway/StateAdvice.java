package datadog.trace.instrumentation.axway;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.axway.AxwayHTTPPluginDecorator.DECORATE;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;

public class StateAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static AgentScope onEnter(
      @Advice.FieldValue String host,
      @Advice.FieldValue String port,
      @Advice.FieldValue java.net.URI uri,
      @Advice.FieldValue Object headers,
      @Advice.This final Object state) {
    System.out.println("host: " + host);
    System.out.println("port: " + port);
    System.out.println("uri: " + uri);
    System.out.println("headers: " + headers);

    try {
      Method m = headers.getClass().getDeclaredMethod("setHeader");
      m.setAccessible(true);
      Object reflectionHost = m.invoke(headers, AxwayHTTPPluginDecorator.CORRELATION_HOST, host);
      Object reflectionPort = m.invoke(headers, AxwayHTTPPluginDecorator.CORRELATION_PORT, port);
      System.out.println("reflectionHost: " + reflectionHost);
      System.out.println("reflectionPort: " + reflectionPort);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
    }

    final AgentSpan span = startSpan(AxwayHTTPPluginDecorator.AXWAY_TRANSACTION);
    final AgentScope scope = activateSpan(span);
    span.setMeasured(true);
    DECORATE.afterStart(span);
    DECORATE.onRequest(span, state);

    return scope;
  }

  //  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  //  public static void onExit(@Advice.FieldValue String host,
  //                            @Advice.FieldValue String port,
  //                            @Advice.FieldValue java.net.URI uri,
  //                            @Advice.FieldValue Object headers,
  //                            @Advice.Enter final AgentScope scope,
  //                            @Advice.Thrown final Throwable throwable) {
  //    if (scope == null) {
  //      return;
  //    }
  //  }
}
