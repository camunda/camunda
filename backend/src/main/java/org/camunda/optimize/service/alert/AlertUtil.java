/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;


public class AlertUtil {

  public static void mapBasicFields(AlertCreationRequestDto toCreate, AlertDefinitionDto result) {
    result.setCheckInterval(toCreate.getCheckInterval());
    result.setEmails(toCreate.getEmails());
    result.setWebhook(toCreate.getWebhook());
    result.setFixNotification(toCreate.isFixNotification());
    result.setName(toCreate.getName());
    result.setReminder(toCreate.getReminder());
    result.setReportId(toCreate.getReportId());
    result.setThreshold(toCreate.getThreshold());
    result.setThresholdOperator(toCreate.getThresholdOperator());
  }
}
