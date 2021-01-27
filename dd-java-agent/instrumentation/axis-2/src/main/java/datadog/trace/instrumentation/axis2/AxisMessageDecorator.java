package datadog.trace.instrumentation.axis2;

import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.BaseDecorator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AxisMessageDecorator extends BaseDecorator {
  public static final CharSequence AXIS2 = UTF8BytesString.createConstant("axis2");
  public static final CharSequence AXIS2_MESSAGE = UTF8BytesString.createConstant("axis2.message");
  public static final AxisMessageDecorator DECORATE = new AxisMessageDecorator();

  private AxisMessageDecorator() {}

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"axis2"};
  }

  @Override
  protected CharSequence spanType() {
    return null;
  }

  @Override
  protected CharSequence component() {
    return AXIS2;
  }
}
