package org.camunda.optimize.test.rule;

import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.springframework.context.ApplicationContext;

/**
 * Is here to allow access to application context for EmbeddedOptimizeRule.
 *
 * @author Askar Akhmerov
 */
public class TestEmbeddedCamundaOptimize extends EmbeddedCamundaOptimize {
  public TestEmbeddedCamundaOptimize(String contextLocation) {
    super(contextLocation);
  }

  @Override
  protected ApplicationContext getApplicationContext() {
    return super.getApplicationContext();
  }
}
