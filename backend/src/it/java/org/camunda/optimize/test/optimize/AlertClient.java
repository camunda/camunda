/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

@AllArgsConstructor
public class AlertClient {

  public static final String TEST_ALERT_NAME = "test alert";

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public String createAlertForReport(final String reportId) {
    return createAlert(createSimpleAlert(reportId));
  }

  public String createAlertForReport(final String reportId, final int intervalValue, final AlertIntervalUnit unit) {
    return createAlert(createSimpleAlert(reportId, intervalValue, unit));
  }

  public String createAlert(final AlertCreationRequestDto creationDto) {
    return getRequestExecutor()
      .buildCreateAlertRequest(creationDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  public Response editAlertAsUser(final String alertId, final AlertCreationRequestDto updatedAlertDto,
                                  final String username, final String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildUpdateAlertRequest(alertId, updatedAlertDto)
      .execute();
  }

  public Response createAlertAsUser(final AlertCreationRequestDto alertCreationRequestDto,
                                    final String username, final String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildCreateAlertRequest(alertCreationRequestDto)
      .execute();
  }

  public Response bulkDeleteAlerts(List<String> alertIds) {
    return getRequestExecutor()
      .buildBulkDeleteAlertsRequest(alertIds)
      .execute();
  }

  public void deleteAlert(String alertId) {
    final Response response = deleteAlertAsUser(alertId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  public List<AlertDefinitionDto> getAllAlerts() {
    return getAllAlerts(DEFAULT_USERNAME, DEFAULT_USERNAME);
  }

  public List<AlertDefinitionDto> getAllAlerts(String username, String password) {
    List<AlertDefinitionDto> result = new ArrayList<>();
    List<EntityResponseDto> entities = getRequestExecutor()
      .buildGetAllEntitiesRequest()
      .withUserAuthentication(username, password)
      .executeAndReturnList(EntityResponseDto.class, 200);

    entities.stream()
      .filter(e -> e.getEntityType().equals(EntityType.COLLECTION))
      .forEach(e -> {
        List<AlertDefinitionDto> alertsOfCollection = getRequestExecutor()
          .buildGetAlertsForCollectionRequest(e.getId())
          .withUserAuthentication(username, password)
          .executeAndReturnList(AlertDefinitionDto.class, Response.Status.OK.getStatusCode());
        result.addAll(alertsOfCollection);
      });

    return result;
  }

  public void updateAlert(String id, AlertCreationRequestDto simpleAlert) {
    getRequestExecutor()
      .buildUpdateAlertRequest(id, simpleAlert)
      .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response deleteAlertAsUser(final String alertId, final String username, final String password) {
    return getRequestExecutor()
      .withUserAuthentication(username, password)
      .buildDeleteAlertRequest(alertId)
      .execute();
  }

  public List<AlertDefinitionDto> getAlertsForCollectionAsDefaultUser(final String collectionId) {
    return getRequestExecutor()
      .buildGetAlertsForCollectionRequest(collectionId)
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .executeAndReturnList(AlertDefinitionDto.class, Response.Status.OK.getStatusCode());
  }

  public AlertCreationRequestDto createSimpleAlert(String reportId) {
    return createSimpleAlert(reportId, 1, AlertIntervalUnit.SECONDS);
  }

  public AlertCreationRequestDto createSimpleAlert(String reportId, int intervalValue, AlertIntervalUnit unit) {
    AlertCreationRequestDto alertCreationRequestDto = new AlertCreationRequestDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit(unit);
    interval.setValue(intervalValue);
    alertCreationRequestDto.setCheckInterval(interval);
    alertCreationRequestDto.setThreshold(0.0);
    alertCreationRequestDto.setThresholdOperator(AlertThresholdOperator.GREATER);
    alertCreationRequestDto.setEmails(Collections.singletonList("test@camunda.com"));
    alertCreationRequestDto.setName(TEST_ALERT_NAME);
    alertCreationRequestDto.setReportId(reportId);

    return alertCreationRequestDto;
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}
