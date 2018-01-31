package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.quartz.Job;
import org.springframework.stereotype.Component;

/**
 * @author Askar Akhmerov
 */
@Component
public class AlertCheckJobFactory extends AbstractAlertFactory {
  private static final Class<? extends Job> ALERT_JOB_CLASS = AlertJob.class;

  protected String getTriggerGroup() {
    return "statusCheck-trigger";
  }

  protected String getTriggerName(AlertDefinitionDto alert) {
    return alert.getId() + "-check-trigger";
  }

  protected String getJobGroup() {
    return "statusCheck-job";
  }

  protected String getJobName(AlertDefinitionDto alert) {
    return alert.getId() + "-check-job";
  }

  @Override
  protected AlertInterval getInterval(AlertDefinitionDto alert) {
    return alert.getCheckInterval();
  }

  @Override
  protected Class<?> getJobClass() {
    return ALERT_JOB_CLASS;
  }
}
