package datadog.trace.instrumentation.http4sblaze;

import datadog.trace.bootstrap.instrumentation.api.URIDataAdapter;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator;
import org.http4s.Request;
import org.http4s.Response;

public class Http4sServerDecorator extends HttpServerDecorator<Request<?>, Request<?>, Response<?>> {

  public static final CharSequence HTTP4S_BLAZE_REQUEST = UTF8BytesString.createConstant("http4s-blaze.request");
  public static final CharSequence HTTP4S_BLAZE_SERVER = UTF8BytesString.createConstant("http4s-blaze-server");
  public static final Http4sServerDecorator DECORATOR = new Http4sServerDecorator();

  @Override
  protected String[] instrumentationNames() { return new String[] {"http4s-blaze", "http4s-blaze-server"}; }

  @Override
  protected CharSequence component() { return HTTP4S_BLAZE_SERVER; }

  @Override
  protected String method(Request<?> request) { return request.method().name(); }

  @Override
  protected URIDataAdapter url(Request<?> request) { return new Http4sUriAdapter(request.uri()); }

  @Override
  protected String peerHostIP(Request<?> request) { return null; }

  @Override
  protected int peerPort(Request<?> request) { return 0; }

  @Override
  protected int status(Response<?> response) { return response.status().code(); }
}
