/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import java.util.List;
import java.util.Optional;

public interface AlertReader {

  long getAlertCount();

  List<AlertDefinitionDto> getStoredAlerts();

  Optional<AlertDefinitionDto> getAlert(String alertId);

  List<AlertDefinitionDto> getAlertsForReport(String reportId);

  List<AlertDefinitionDto> getAlertsForReports(List<String> reportIds);
}
