/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class AlertRestServiceIT extends AbstractAlertIT {

  private static final String TEST = "test";

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  @Test
  public void createNewAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildCreateAlertRequest(null)
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void cantCreateWithoutReport() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(new AlertCreationDto())
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(method = "definitionType")
  public void cantUpdateWithoutReport(final int definitionType) {
    //given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);
    alert.setReportId(TEST);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateAlertRequest(id, alert)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  @Parameters(method = "definitionType")
  public void cantCreateAlertForPrivateReports(final int definitionType) {
    //given
    String reportId = createNumberReportForCollection(null, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute();

    // then
    assertThat(response.getStatus(), is(400));
  }

  @Test
  @Parameters(method = "definitionType")
  public void createNewAlert(final int definitionType) {
    //given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);

    // when
    String id = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, 200);

    // then
    assertThat(id, is(notNullValue()));
  }

  @Test
  @Parameters(method = "definitionType")
  public void createNewAlertAllowsMaxInt(final int definitionType) {
    //given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);
    alert.setThreshold(Integer.MAX_VALUE);

    // when
    String id = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, 200);

    // then
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void updateAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateAlertRequest("1", new AlertCreationDto())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "definitionType")
  public void updateNonExistingAlert(final int definitionType) {
    // given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateAlertRequest("nonExistingId", createSimpleAlert(reportId))
      .execute();

    // then 
    assertThat(response.getStatus(), is(404));
  }

  @Test
  @Parameters(method = "definitionType")
  public void updateAlert(final int definitionType) {
    //given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);
    alert.setEmail("new@camunda.com");


    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildUpdateAlertRequest(id, alert)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredAlertsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllAlertsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "definitionType")
  public void getStoredAlerts(final int definitionType) {
    //given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);

    // when
    List<AlertDefinitionDto> allAlerts = getAllAlerts();

    // then
    assertThat(allAlerts.size(), is(1));
    assertThat(allAlerts.get(0).getId(), is(id));
  }

  @Test
  public void deleteAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteAlertRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  @Parameters(method = "definitionType")
  public void deleteNewAlert(final int definitionType) {
    //given
    String collectionId = createNewCollection();
    String reportId = createNumberReportForCollection(collectionId, definitionType);
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteAlertRequest(id)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getAllAlerts().size(), is(0));
  }

  @Test
  public void deleteNonExistingAlert() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteAlertRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  private String addAlertToOptimize(AlertCreationDto creationDto) {
    return addAlertToOptimizeAsUser(creationDto, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String addAlertToOptimizeAsUser(final AlertCreationDto creationDto,
                                          final String user,
                                          final String password) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateAlertRequest(creationDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }
}
