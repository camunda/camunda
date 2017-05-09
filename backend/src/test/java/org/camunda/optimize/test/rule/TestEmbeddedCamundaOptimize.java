package org.camunda.optimize.test.rule;

import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.camunda.optimize.service.importing.job.schedule.ScheduleJobFactory;
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

  public ScheduleJobFactory getImportScheduleFactory() {
    return getApplicationContext().getBean(ScheduleJobFactory.class);
  }
}
