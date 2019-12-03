/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class AlertClient {

  private final EmbeddedOptimizeExtension embeddedOptimizeExtension;

  public String createAlertForReport(String reportId) {
    return addAlertToOptimizeAsUser(createSimpleAlert(reportId));
  }

  public Response editAlertAsUser(final String alertId, final AlertCreationDto updatedAlertDto,
                                     final String username, final String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildUpdateAlertRequest(alertId, updatedAlertDto)
      .execute();
  }

  public Response createAlertAsUser(final AlertCreationDto alertCreationDto,
                                       final String username, final String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildCreateAlertRequest(alertCreationDto)
      .execute();
  }

  public Response deleteAlertAsUser(final String alertId, final String username, final String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildDeleteAlertRequest(alertId)
      .execute();
  }

  public List<AlertDefinitionDto> getAlertsForCollectionAsDefaultUser(final String collectionId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }

  private String addAlertToOptimizeAsUser(final AlertCreationDto creationDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(creationDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private AlertCreationDto createSimpleAlert(String reportId) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit("Seconds");
    interval.setValue(1);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }
}
