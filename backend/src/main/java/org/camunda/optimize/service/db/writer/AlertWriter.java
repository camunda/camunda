/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

import java.util.List;

public interface AlertWriter {

  AlertDefinitionDto createAlert(AlertDefinitionDto alertDefinitionDto);

  void updateAlert(AlertDefinitionDto alertUpdate);

  void deleteAlert(String alertId);

  void deleteAlerts(List<String> alertIds);

  void deleteAlertsForReport(String reportId);

  void writeAlertTriggeredStatus(boolean alertStatus, String alertId);
}
