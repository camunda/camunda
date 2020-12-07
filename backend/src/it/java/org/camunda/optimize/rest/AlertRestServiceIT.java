/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class AlertRestServiceIT extends AbstractAlertIT {

  private static final String TEST = "test";

  private static Stream<DefinitionType> definitionType() {
    return Stream.of(DefinitionType.PROCESS, DefinitionType.DECISION);
  }

  @Test
  public void createNewAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateAlertRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void cantCreateWithoutReport() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(new AlertCreationRequestDto())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest(name = "cannot update alert without report with definition type {0}")
  @MethodSource("definitionType")
  public void cantUpdateWithoutReport(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    String id = alertClient.createAlert(alert);
    alert.setReportId(TEST);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(id, alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest(name = "cannot create for private reports with definition type {0}")
  @MethodSource("definitionType")
  public void cantCreateAlertForPrivateReports(final DefinitionType definitionType) {
    // given
    String reportId = createNumberReportForCollection(null, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest(name = "create new report with report definition type {0}")
  @MethodSource("definitionType")
  public void createNewAlert(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);

    // when
    String id = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(id).isNotNull();
  }

  @Test
  public void createNewAlert_atLeastOneNotificationServiceNeedsToBeDefined() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    String reportId = createNumberReportForCollection(collectionId, DefinitionType.PROCESS);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);

    // when
    alert.setEmails(new ArrayList<>());
    alert.setWebhook(null);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(String.class))
      .contains("The fields [emails] and [webhook] are not allowed to both be empty");

    // when
    alert.setEmails(new ArrayList<>());
    alert.setWebhook("foo");
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when
    alert.setEmails(Collections.singletonList("foo@bar.com"));
    alert.setWebhook(null);
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest(name = "create new alert with max threshold and report definition type {0}")
  @MethodSource("definitionType")
  public void createNewAlertAllowsMaxDouble(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    alert.setThreshold(Double.MAX_VALUE);

    // when
    String id = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(id).isNotNull();
  }

  @Test
  public void updateAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateAlertRequest("1", new AlertCreationRequestDto())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest(name = "update existing alert for report with definition type {0}")
  @MethodSource("definitionType")
  public void updateNonExistingAlert(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest("nonExistingId", alertClient.createSimpleAlert(reportId))
      .execute();

    // then 
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @ParameterizedTest(name = "update alert for report with definition type {0}")
  @MethodSource("definitionType")
  public void updateAlert(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    String id = alertClient.createAlert(alert);
    alert.setEmails(Collections.singletonList("new@camunda.com"));


    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(id, alert)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void updateAlert_atLeastOneNotificationServiceNeedsToBeDefined() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    String reportId = createNumberReportForCollection(collectionId, DefinitionType.PROCESS);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    String alertId = alertClient.createAlert(alert);

    // when
    alert.setEmails(new ArrayList<>());
    alert.setWebhook(null);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(alertId, alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.readEntity(String.class))
      .contains("The fields [emails] and [webhook] are not allowed to both be empty");

    // when
    alert.setEmails(new ArrayList<>());
    alert.setWebhook("foo");
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(alertId, alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());

    // when
    alert.setEmails(Collections.singletonList("foo@bar.com"));
    alert.setWebhook(null);
    response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(alertId, alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void getStoredAlerts(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    String id = alertClient.createAlert(alert);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAlertsForCollectionAsDefaultUser(collectionId);

    // then
    assertThat(allAlerts.size()).isEqualTo(1);
    assertThat(allAlerts.get(0).getId()).isEqualTo(id);
  }

  @Test
  public void deleteAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteAlertRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void deleteNewAlert(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    String id = alertClient.createAlert(alert);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteAlertRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(alertClient.getAllAlerts().size()).isZero();
  }

  @Test
  public void deleteNonExistingAlert() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteAlertRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

}
