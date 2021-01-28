package datadog.trace.instrumentation.http4sblaze;

import cats.data.Kleisli;
import cats.data.Kleisli$;
import cats.effect.ConcurrentEffect;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.http4s.Request;
import org.http4s.Response;
import scala.Function1;
import scala.util.Either;

import java.util.Map;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.*;
import static datadog.trace.instrumentation.http4sblaze.Http4sServerDecorator.DECORATOR;
import static datadog.trace.instrumentation.http4sblaze.Http4sServerDecorator.HTTP4S_BLAZE_REQUEST;
import static datadog.trace.instrumentation.http4sblaze.Http4sServerHeaders.HEADERS;
import static java.util.Collections.singletonMap;

@Slf4j
@AutoService(Instrumenter.class)
public class BlazeServerBuilderInstrumentation extends Instrumenter.Tracing {

  public BlazeServerBuilderInstrumentation() {
    super("http4s-blaze", "http4s-blaze-server");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("org.http4s.server.blaze.BlazeServerBuilder");
  }

  @Override
  public String[] helperClassNames() {
    return new String[]{
      getClass().getName() + "$DatadogWrapperHelper",
      getClass().getName() + "$WithHttpAppAdvice",
      getClass().getName() + "$WithHttpAppAdvice$RequestHandler",
      getClass().getName() + "$WithHttpAppAdvice$ResponseHandler",
      packageName + ".Http4sServerDecorator",
      packageName + ".Http4sServerHeaders",
      packageName + ".Http4sUriAdapter",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
      named("withHttpApp"),
      getClass().getName() + "$WithHttpAppAdvice");
  }

  public static class WithHttpAppAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static <F> void enter(
      @Advice.Argument(value = 0, readOnly = false)
        Kleisli<F, Request<F>, Response<F>> httpApp,
      @Advice.FieldValue(value = "F", readOnly = true) final ConcurrentEffect<F> effect
    ) {
      final RequestHandler<F> requestHandler = new RequestHandler<F>(effect, httpApp);
      httpApp = Kleisli$.MODULE$.apply(requestHandler);
    }

    public static class RequestHandler<F> implements Function1<Request<F>, F> {
      private final ConcurrentEffect<F> effect;
      private final Kleisli<F, Request<F>, Response<F>> baseApp;

      public RequestHandler(ConcurrentEffect<F> effect, Kleisli<F, Request<F>, Response<F>> baseApp) {
        this.effect = effect;
        this.baseApp = baseApp;
      }

      public F apply(Request<F> request) {
        final AgentScope scope = DatadogWrapperHelper.createSpan(request);
        final ResponseHandler<F> responseHandler = new ResponseHandler<F>(effect, scope);
        return effect.flatMap(
          effect.attempt(baseApp.apply(request)),
          responseHandler
        );
      }
    }

    public static class ResponseHandler<F> implements Function1<Either<Throwable, Response<F>>, F> {
      private final ConcurrentEffect<F> effect;
      private final AgentScope scope;

      public ResponseHandler(ConcurrentEffect<F> effect, AgentScope scope) {
        this.effect = effect;
        this.scope = scope;
      }

      public F apply(Either<Throwable, Response<F>> result) {
        if (result.isRight()) {
          final Response<F> response = result.right().get();
          DatadogWrapperHelper.finishSpan(scope.span(), response);
          return effect.pure(response);
        } else {
          final Throwable error = result.left().get();
          DatadogWrapperHelper.finishSpan(scope.span(), error);
          return effect.raiseError(error);
        }
      }
    }
  }

  public static class DatadogWrapperHelper {
    public static AgentScope createSpan(final Request<?> request) {
      final AgentSpan.Context extractedContext = propagate().extract(request, HEADERS);
      final AgentSpan span = startSpan(HTTP4S_BLAZE_REQUEST, extractedContext);
      span.setMeasured(true);

      DECORATOR.afterStart(span);
      DECORATOR.onConnection(span, request);
      DECORATOR.onRequest(span, request);

      final AgentScope scope = activateSpan(span);
      scope.setAsyncPropagation(true);
      return scope;
    }

    public static void finishSpan(final AgentSpan span, final Response<?> response) {
      DECORATOR.onResponse(span, response);
      DECORATOR.beforeFinish(span);

      span.finish();
    }

    public static void finishSpan(final AgentSpan span, final Throwable t) {
      DECORATOR.onError(span, t);
      span.setTag(Tags.HTTP_STATUS, 500);
      DECORATOR.beforeFinish(span);

      span.finish();
    }
  }
}
