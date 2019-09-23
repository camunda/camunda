/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.junit.Test;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class AlertAuthorizationIT extends AbstractAlertIT {

  @Test
  public void getOwnAuthorizedAlertsOnly() {
    //given
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");

    AlertCreationDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationDto alert2 = setupBasicProcessAlertAsUser("processDefinition1", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final String ownAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()), contains(ownAlertId));
  }

  @Test
  public void superUserGetAllAlerts() {
    //given
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    AlertCreationDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationDto alert2 = setupBasicProcessAlertAsUser("processDefinition1", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final String alertId1 = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    final String alertId2 = addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(
      allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()),
      containsInAnyOrder(alertId1, alertId2)
    );
  }

  @Test
  public void superUserGetAllAlertsOnlyForAuthorizedDefinitions() {
    //given
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    AlertCreationDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationDto alert2 = setupBasicProcessAlertAsUser("processDefinition2", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final String authorizedAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()), contains(authorizedAlertId));
  }

  @Test
  public void superUserGetAllAlertsOfCollectionReports() {
    //given
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String definitionKey = "processDefinition1";
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(definitionKey);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, definitionKey);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final String alertId = createAlertInCollectionAsDefaultUser(processDefinition);

    // when
    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()), containsInAnyOrder(alertId));
  }

  @Test
  public void superUserGetAllAlertsOfCollectionReportsOnlyForAuthorizedDefinitions() {
    //given
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String definitionKey1 = "processDefinition1";
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleServiceTaskProcess(definitionKey1);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, definitionKey1);

    final String definitionKey2 = "processDefinition2";
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcess(definitionKey2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    final String authorizedAlertId = createAlertInCollectionAsDefaultUser(processDefinition1);
    createAlertInCollectionAsDefaultUser(processDefinition2);

    // when
    List<AlertDefinitionDto> allAlerts = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllAlertsRequest()
      .executeAndReturnList(AlertDefinitionDto.class, 200);

    // then
    assertThat(
      allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()),
      containsInAnyOrder(authorizedAlertId)
    );
  }

  private String createAlertInCollectionAsDefaultUser(final ProcessDefinitionEngineDto processDefinition) {
    final String collectionId = createNewCollectionAsDefaultUser();

    final String reportId = createSingleProcessReportInCollection(collectionId);
    final SingleProcessReportDefinitionDto numberReportDefinitionDto = getNumberReportDefinitionDto(processDefinition);
    updateSingleProcessReport(reportId, numberReportDefinitionDto);

    return addAlertToOptimizeAsUser(createSimpleAlert(reportId), DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createSingleProcessReportInCollection(final String collectionId) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(collectionId)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewCollectionAsDefaultUser() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCollectionRequest()
      .execute(IdDto.class, 200)
      .getId();
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

}
