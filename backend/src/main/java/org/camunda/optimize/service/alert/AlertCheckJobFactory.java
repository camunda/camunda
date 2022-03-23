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
public class AlertCheckJobFactory extends AbstractAlertFactory<AlertJob> {

  public AlertCheckJobFactory(final ApplicationContext applicationContext) {
    super(applicationContext);
  }

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
  protected Class<AlertJob> getJobClass() {
    return AlertJob.class;
  }
}
