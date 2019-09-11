/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public class AlertRestServiceIT extends AbstractAlertIT {

  private static final String TEST = "test";

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Test
  public void createNewAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateAlertRequest(new AlertCreationDto())
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantUpdateWithoutReport() {
    //given
    String reportId = createNumberReport();
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);
    alert.setReportId(TEST);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateAlertRequest(id, alert)
      .execute();

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewAlert() {
    //given
    String reportId = createNumberReport();
    AlertCreationDto alert = createSimpleAlert(reportId);

    // when
    String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, 200);

    // then the status code is okay
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void createNewAlertAllowsMaxInt() {
    //given
    String reportId = createNumberReport();
    AlertCreationDto alert = createSimpleAlert(reportId);
    alert.setThreshold(Integer.MAX_VALUE);

    // when
    String id = embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateAlertRequest(alert)
      .execute(String.class, 200);

    // then the status code is okay
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void updateAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildUpdateAlertRequest("1", new AlertCreationDto())
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void updateNonExistingAlert() {
    // given
    String reportId = createNumberReport();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateAlertRequest("nonExistingId", createSimpleAlert(reportId))
      .execute();

    // then 
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void updateAlert() {
    //given
    String reportId = createNumberReport();
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);
    alert.setEmail("new@camunda.com");


    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateAlertRequest(id, alert)
      .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void getStoredAlertsWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildGetAllAlertsRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void getStoredAlerts() {
    //given
    String reportId = createNumberReport();
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);

    // when
    List<AlertDefinitionDto> allAlerts = getAllAlerts();

    // then
    assertThat(allAlerts.size(), is(1));
    assertThat(allAlerts.get(0).getId(), is(id));
  }

  @Test
  public void getAuthorizedAlertsOnly() {
    //given
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");

    AlertCreationDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationDto alert2 = setupBasicProcessAlertAsUser("processDefinition2", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(allAlerts.size(), is(1));
  }

  @Test
  public void deleteAlertWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildDeleteAlertRequest("1124")
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNewAlert() {
    //given
    String reportId = createNumberReport();
    AlertCreationDto alert = createSimpleAlert(reportId);
    String id = addAlertToOptimize(alert);

    // when
    Response response = embeddedOptimizeRule
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
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildDeleteAlertRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void emailNotificationIsEnabledCheckWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withoutAuthentication()
      .buildEmailNotificationIsEnabledRequest()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void emailNotificationIsEnabledCheckWithAuthentication() {
    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEmailNotificationIsEnabledRequest()
      .execute();

    // then the status code is authorized
    assertThat(response.getStatus(), is(200));
  }

  private String addAlertToOptimize(AlertCreationDto creationDto) {
    return addAlertToOptimizeAsUser(creationDto, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String addAlertToOptimizeAsUser(final AlertCreationDto creationDto,
                                          final String user,
                                          final String password) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateAlertRequest(creationDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private List<AlertDefinitionDto> getAllAlerts() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);
  }

  private String createNumberReport() {
    ProcessDefinitionEngineDto engineDto = new ProcessDefinitionEngineDto();
    engineDto.setKey("Foo");
    engineDto.setVersion(1);
    return createAndStoreNumberReport(engineDto);
  }
}
