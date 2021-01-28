package datadog.trace.instrumentation.http4sblaze;

import datadog.trace.bootstrap.instrumentation.api.URIDataAdapter;
import org.http4s.Uri;
import scala.Option;

public class Http4sUriAdapter implements URIDataAdapter {

  private final Uri uri;

  public Http4sUriAdapter(Uri uri) {
    this.uri = uri;
  }

  @Override
  public String scheme() {
    Option<Uri.Scheme> scheme = uri.scheme();
    if (scheme.isEmpty()) return "";
    return scheme.get().value();
  }

  @Override
  public String host() {
    Option<Uri.Host> host = uri.host();
    if (host.isEmpty()) return "";
    return host.get().value();
  }

  @Override
  public int port() {
    Option<Object> port = uri.port();
    if (port.isEmpty()) return 0;
    return (int) port.get();
  }

  @Override
  public String path() {
    return uri.path();
  }

  @Override
  public String fragment() {
    Option<String> fragment = uri.fragment();
    if (fragment.isEmpty()) return "";
    return fragment.get();
  }

  @Override
  public String query() {
    return uri.query().renderString();
  }
}
