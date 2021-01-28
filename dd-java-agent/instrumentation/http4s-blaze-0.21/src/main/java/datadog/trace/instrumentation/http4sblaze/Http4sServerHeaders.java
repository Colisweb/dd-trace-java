package datadog.trace.instrumentation.http4sblaze;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import org.http4s.Header;
import org.http4s.Request;
import scala.collection.JavaConverters;

public class Http4sServerHeaders implements AgentPropagation.ContextVisitor<Request<?>> {

  public static final Http4sServerHeaders HEADERS = new Http4sServerHeaders();

  @Override
  public void forEachKey(Request<?> carrier, AgentPropagation.KeyClassifier classifier) {
    final scala.collection.immutable.List<Header> headers = carrier.headers();
    for (final Header header : JavaConverters.asJavaCollection(headers)) {
      if (!classifier.accept(header.name().value().toLowerCase(), header.value())) {
        return;
      }
    }
  }
}
