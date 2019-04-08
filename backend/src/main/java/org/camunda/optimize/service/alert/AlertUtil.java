/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

import java.time.OffsetDateTime;


public class AlertUtil {

  public static void mapBasicFields(AlertCreationDto toCreate, AlertDefinitionDto result) {
    result.setCheckInterval(toCreate.getCheckInterval());
    result.setEmail(toCreate.getEmail());
    result.setFixNotification(toCreate.isFixNotification());
    result.setName(toCreate.getName());
    result.setReminder(toCreate.getReminder());
    result.setReportId(toCreate.getReportId());
    result.setThreshold(toCreate.getThreshold());
    result.setThresholdOperator(toCreate.getThresholdOperator());
  }

  public static void updateFromUser(String userId, AlertDefinitionDto result) {
    result.setLastModified(OffsetDateTime.now());
    result.setLastModifier(userId);
  }
}
