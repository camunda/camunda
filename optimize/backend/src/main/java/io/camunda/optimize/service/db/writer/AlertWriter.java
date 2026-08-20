/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.writer;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import java.util.List;

public interface AlertWriter {

  AlertDefinitionDto createAlert(AlertDefinitionDto alertDefinitionDto);

  void updateAlert(AlertDefinitionDto alertUpdate);

  void deleteAlert(String alertId);

  void deleteAlerts(List<String> alertIds);

  void deleteAlertsForReport(String reportId);

  void writeAlertTriggeredStatus(boolean alertStatus, String alertId);
}
