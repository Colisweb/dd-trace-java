package datadog.trace.instrumentation.axis2;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.namedOneOf;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.axis2.AxisMessageDecorator.AXIS2_MESSAGE;
import static datadog.trace.instrumentation.axis2.AxisMessageDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Tracer;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.axis2.context.MessageContext;

@AutoService(Instrumenter.class)
public class AxisEngineInstrumentation extends Instrumenter.Tracing {

  public AxisEngineInstrumentation() {
    super("axis2");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.apache.axis2.engine.AxisEngine");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".AxisMessageDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(namedOneOf("receive", "send", "sendFault"))
            .and(takesArgument(0, named("org.apache.axis2.context.MessageContext"))),
        getClass().getName() + "$InvokeMessageAdvice");
    transformers.put(
        isMethod()
            .and(namedOneOf("flowComplete"))
            .and(takesArgument(0, named("org.apache.axis2.context.MessageContext"))),
        getClass().getName() + "$CompleteMessageAdvice");
    return transformers;
  }

  public static final class InvokeMessageAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void start(@Advice.Argument(0) final MessageContext context) {
      Object spanData = context.getSelfManagedData(Tracer.class, "span");
      if (null == spanData) {
        AgentSpan span = startSpan(AXIS2_MESSAGE);
        DECORATE.afterStart(span);
        activateSpan(span);
        context.setSelfManagedData(Tracer.class, "span", span);
      }
    }
  }

  public static final class CompleteMessageAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Argument(0) final MessageContext context, @Advice.Thrown final Throwable error) {
      Object spanData = context.getSelfManagedData(Tracer.class, "span");
      if (spanData instanceof AgentSpan) {
        AgentSpan span = (AgentSpan) spanData;
        DECORATE.onError(span, error);
        DECORATE.beforeFinish(span);
        span.finish();
        context.removeSelfManagedData(Tracer.class, "span");
      }
    }
  }
}
