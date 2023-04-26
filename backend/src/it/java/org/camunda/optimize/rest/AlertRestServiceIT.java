/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import io.github.netmikey.logunit.api.LogCapturer;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_PER_GROUP_BY_NONE;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;

public class AlertRestServiceIT extends AbstractAlertIT {

  @RegisterExtension
  @Order(5)
  protected final LogCapturer logCapturer =
    LogCapturer.create().forLevel(Level.DEBUG).captureForType(AlertService.class);

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
  public void createNewAlertWithForPercentageReport() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess(DEFAULT_DEFINITION_KEY);
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(
      TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
        .setProcessDefinitionKey(processDefinition.getKey())
        .setProcessDefinitionVersion(processDefinition.getVersionAsString())
        .build()
    );
    reportDef.setCollectionId(collectionId);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    alert.setThreshold(50.);

    // when
    String id = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, Response.Status.OK.getStatusCode());

    // then
    assertThat(id).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(doubles = { 101.0, -1.0 })
  public void createNewAlertWithThresholdNotInValidRange(final double threshold) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess(DEFAULT_DEFINITION_KEY);
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(
      TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
        .setProcessDefinitionKey(processDefinition.getKey())
        .setProcessDefinitionVersion(processDefinition.getVersionAsString())
        .build()
    );
    reportDef.setCollectionId(collectionId);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    alert.setThreshold(threshold);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewAlertWithNullThreshold() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess(DEFAULT_DEFINITION_KEY);
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(
      TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
        .setProcessDefinitionKey(processDefinition.getKey())
        .setProcessDefinitionVersion(processDefinition.getVersionAsString())
        .build()
    );
    reportDef.setCollectionId(collectionId);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    alert.setThreshold(null);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(doubles = { 101.0, -1.0 })
  public void updateAlertWithThresholdNotInValidRange(final double threshold) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess(DEFAULT_DEFINITION_KEY);
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(
      TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
        .setProcessDefinitionKey(processDefinition.getKey())
        .setProcessDefinitionVersion(processDefinition.getVersionAsString())
        .build()
    );
    reportDef.setCollectionId(collectionId);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    alert.setThreshold(50.);
    final String savedAlert = alertClient.createAlert(alert);

    // when
    alert.setThreshold(threshold);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(savedAlert, alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void updateAlertWithNullThreshold() {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(DefinitionType.PROCESS);
    final ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess(DEFAULT_DEFINITION_KEY);
    SingleProcessReportDefinitionRequestDto reportDef = new SingleProcessReportDefinitionRequestDto(
      TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(PROC_INST_PER_GROUP_BY_NONE)
        .setProcessDefinitionKey(processDefinition.getKey())
        .setProcessDefinitionVersion(processDefinition.getVersionAsString())
        .build()
    );
    reportDef.setCollectionId(collectionId);
    final String reportId = reportClient.createSingleProcessReport(reportDef);
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId);
    alert.setThreshold(50.);
    final String savedAlert = alertClient.createAlert(alert);

    // when
    alert.setThreshold(null);
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildUpdateAlertRequest(savedAlert, alert)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
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

    // then
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
    AlertCreationRequestDto alert = alertClient.createSimpleAlert(reportId, 1, AlertIntervalUnit.HOURS);
    String id = alertClient.createAlert(alert);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteAlertRequest(id)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(alertClient.getAllAlerts()).isEmpty();
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

  @Test
  public void bulkDeleteAlertsNoAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildBulkDeleteAlertsRequest(Collections.emptyList())
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void bulkDeleteAlertsNoUserAuthorization(final DefinitionType definitionType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    String alertId1 = alertClient.createAlertForReport(reportId, 1, AlertIntervalUnit.HOURS);
    String alertId2 = alertClient.createAlertForReport(reportId, 1, AlertIntervalUnit.HOURS);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildBulkDeleteAlertsRequest(Arrays.asList(alertId1, alertId2))
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void bulkDeleteEmptyListOfAlerts() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildBulkDeleteAlertsRequest(Collections.emptyList())
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
  }

  @Test
  public void bulkDeleteNullListOfAlerts() {
    // when
    Response response = alertClient.bulkDeleteAlerts(null);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void bulkDeleteAlerts(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    String alertId1 = alertClient.createAlertForReport(reportId, 1, AlertIntervalUnit.HOURS);
    String alertId2 = alertClient.createAlertForReport(reportId, 1, AlertIntervalUnit.HOURS);

    // when
    Response response = alertClient.bulkDeleteAlerts(Arrays.asList(alertId1, alertId2));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(alertClient.getAllAlerts()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void bulkDeleteAlertsOnlyDeletesAlertsThatExistInBulk(final DefinitionType definitionType) {
    // given
    String collectionId = collectionClient.createNewCollectionWithDefaultScope(definitionType);
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    String alertId1 = alertClient.createAlertForReport(reportId, 1, AlertIntervalUnit.HOURS);
    String alertId2 = alertClient.createAlertForReport(reportId, 1, AlertIntervalUnit.HOURS);

    // when
    Response response = alertClient.bulkDeleteAlerts(Arrays.asList(alertId1, "doesntExist", alertId2));

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(alertClient.getAllAlerts()).isEmpty();
    logCapturer.assertContains("Cannot find alert with id [doesntExist], it may have been deleted already");
  }

}
