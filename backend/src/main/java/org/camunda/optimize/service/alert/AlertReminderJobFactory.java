/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class AlertReminderJobFactory extends AbstractAlertFactory<AlertJob> {

  public AlertReminderJobFactory(final ApplicationContext applicationContext) {
    super(applicationContext);
  }

  @Override
  protected String getTriggerGroup() {
    return "statusReminder-trigger";
  }

  @Override
  protected String getTriggerName(final AlertDefinitionDto alert) {
    return alert.getId() + "-reminder-trigger";
  }

  @Override
  protected String getJobGroup() {
    return "statusReminder-job";
  }

  @Override
  protected String getJobName(final AlertDefinitionDto alert) {
    return alert.getId() + "-reminder-job";
  }

  @Override
  protected AlertInterval getInterval(final AlertDefinitionDto alert) {
    return alert.getReminder();
  }

  @Override
  protected Class<AlertJob> getJobClass() {
    return AlertJob.class;
  }
}
