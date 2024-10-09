/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import io.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import io.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import io.camunda.optimize.dto.optimize.query.alert.AlertThresholdOperator;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class AlertClient {

  public static final String TEST_ALERT_NAME = "test alert";

  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public AlertClient(final Supplier<OptimizeRequestExecutor> requestExecutorSupplier) {
    this.requestExecutorSupplier = requestExecutorSupplier;
  }

  public String createAlertForReport(final String reportId) {
    return createAlert(createSimpleAlert(reportId));
  }

  public String createAlertForReport(
      final String reportId, final int intervalValue, final AlertIntervalUnit unit) {
    return createAlert(createSimpleAlert(reportId, intervalValue, unit));
  }

  public String createAlert(final AlertCreationRequestDto creationDto) {
    return getRequestExecutor()
        .buildCreateAlertRequest(creationDto)
        .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
        .getId();
  }

  public Response editAlertAsUser(
      final String alertId,
      final AlertCreationRequestDto updatedAlertDto,
      final String username,
      final String password) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildUpdateAlertRequest(alertId, updatedAlertDto)
        .execute();
  }

  public Response createAlertAsUser(
      final AlertCreationRequestDto alertCreationRequestDto,
      final String username,
      final String password) {
    return getRequestExecutor()
        .withUserAuthentication(username, password)
        .buildCreateAlertRequest(alertCreationRequestDto)
        .execute();
  }

  public Response bulkDeleteAlerts(final List<String> alertIds) {
    return getRequestExecutor().buildBulkDeleteAlertsRequest(alertIds).execute();
  }

  public void deleteAlert(final String alertId) {
    final Response response = deleteAlertAsUser(alertId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  public List<AlertDefinitionDto> getAllAlerts() {
    return getAllAlerts(DEFAULT_USERNAME, DEFAULT_USERNAME);
  }

  public List<AlertDefinitionDto> getAllAlerts(final String username, final String password) {
    final List<AlertDefinitionDto> result = new ArrayList<>();
    final List<EntityResponseDto> entities =
        getRequestExecutor()
            .buildGetAllEntitiesRequest()
            .withUserAuthentication(username, password)
            .executeAndReturnList(EntityResponseDto.class, 200);

    entities.stream()
        .filter(e -> e.getEntityType().equals(EntityType.COLLECTION))
        .forEach(
            e -> {
              final List<AlertDefinitionDto> alertsOfCollection =
                  getRequestExecutor()
                      .buildGetAlertsForCollectionRequest(e.getId())
                      .withUserAuthentication(username, password)
                      .executeAndReturnList(
                          AlertDefinitionDto.class, Response.Status.OK.getStatusCode());
              result.addAll(alertsOfCollection);
            });

    return result;
  }

  public void updateAlert(final String id, final AlertCreationRequestDto simpleAlert) {
    getRequestExecutor()
        .buildUpdateAlertRequest(id, simpleAlert)
        .execute(Response.Status.NO_CONTENT.getStatusCode());
  }

  public Response deleteAlertAsUser(
      final String alertId, final String username, final String password) {
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

  public AlertCreationRequestDto createSimpleAlert(final String reportId) {
    return createSimpleAlert(reportId, 1, AlertIntervalUnit.SECONDS);
  }

  public AlertCreationRequestDto createSimpleAlert(
      final String reportId, final int intervalValue, final AlertIntervalUnit unit) {
    final AlertCreationRequestDto alertCreationRequestDto = new AlertCreationRequestDto();

    final AlertInterval interval = new AlertInterval();
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
