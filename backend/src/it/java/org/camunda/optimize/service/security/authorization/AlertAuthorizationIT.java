/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class AlertAuthorizationIT extends AbstractAlertIT {

  @Test
  public void getOwnAuthorizedAlertsOnly() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser("processDefinition2", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final String ownAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()), contains(ownAlertId));
  }

  @Test
  public void superUserGetAllAlerts() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser("processDefinition1", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final String alertId1 = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    final String alertId2 = addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(
      allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()),
      containsInAnyOrder(alertId1, alertId2)
    );
  }

  @Test
  public void superUserGetAllAlertsOnlyForAuthorizedDefinitions() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, "processDefinition1");
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser("processDefinition1", KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser("processDefinition2", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    final String authorizedAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()), contains(authorizedAlertId));
  }

  @Test
  public void superUserGetAllAlertsOfCollectionReports() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String definitionKey = "processDefinition1";
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(definitionKey);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, definitionKey);

    importAllEngineEntitiesFromScratch();

    final String alertId = createAlertInCollectionAsDefaultUser(processDefinition);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()), containsInAnyOrder(alertId));
  }

  @Test
  public void superUserGetAllAlertsOfCollectionReportsOnlyForAuthorizedDefinitions() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    embeddedOptimizeExtension.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String definitionKey1 = "processDefinition1";
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleServiceTaskProcess(definitionKey1);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, definitionKey1);

    final String definitionKey2 = "processDefinition2";
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcess(definitionKey2);

    importAllEngineEntitiesFromScratch();

    final String authorizedAlertId = createAlertInCollectionAsDefaultUser(processDefinition1);
    createAlertInCollectionAsDefaultUser(processDefinition2);

    // when
    List<AlertDefinitionDto> allAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(
      allAlerts.stream().map(AlertDefinitionDto::getId).collect(toList()),
      containsInAnyOrder(authorizedAlertId)
    );
  }

  private String createAlertInCollectionAsDefaultUser(final ProcessDefinitionEngineDto processDefinition) {
    final String collectionId = collectionClient.createNewCollectionWithProcessScope(processDefinition);
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto = getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition
    );
    final String reportId = createSingleProcessReportInCollection(singleProcessReportDefinitionDto);

    final SingleProcessReportDefinitionRequestDto numberReportDefinitionDto = getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition
    );
    reportClient.updateSingleProcessReport(reportId, numberReportDefinitionDto);

    return addAlertToOptimizeAsUser(alertClient.createSimpleAlert(reportId), DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createSingleProcessReportInCollection(final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private String addAlertToOptimizeAsUser(final AlertCreationRequestDto creationDto,
                                          final String user,
                                          final String password) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateAlertRequest(creationDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

}
