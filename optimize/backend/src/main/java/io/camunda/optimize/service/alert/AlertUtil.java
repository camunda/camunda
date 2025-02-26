/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.alert;

import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

public class AlertUtil {

  public static void mapBasicFields(
      final AlertCreationRequestDto toCreate, final AlertDefinitionDto result) {
    result.setCheckInterval(toCreate.getCheckInterval());
    result.setEmails(toCreate.getEmails());
    result.setFixNotification(toCreate.isFixNotification());
    result.setName(toCreate.getName());
    result.setReminder(toCreate.getReminder());
    result.setReportId(toCreate.getReportId());
    result.setThreshold(toCreate.getThreshold());
    result.setThresholdOperator(toCreate.getThresholdOperator());
  }
}
