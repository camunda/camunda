/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.authorization;

import org.camunda.optimize.AbstractAlertIT;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.importing.EngineConstants.ALL_PERMISSION;
import static org.camunda.optimize.service.util.importing.EngineConstants.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

public class AlertAuthorizationIT extends AbstractAlertIT {

  public final String PROCESS_DEFINITION_KEY = "processDefinition";
  public final String PROCESS_DEFINITION_KEY_2 = "processDefinition2";

  @Test
  public void getOwnAuthorizedAlertsOnly() {
    // given
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, PROCESS_DEFINITION_KEY);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser(PROCESS_DEFINITION_KEY, KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser(
      PROCESS_DEFINITION_KEY_2,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
    final String ownAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactly(ownAlertId);
  }

  @Test
  public void superUserGetAllAlerts() {
    // given
    createSuperUserAuthorization();
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, PROCESS_DEFINITION_KEY);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser(PROCESS_DEFINITION_KEY, KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser(
      PROCESS_DEFINITION_KEY,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
    final String alertId1 = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    final String alertId2 = addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactlyInAnyOrder(alertId1, alertId2);
  }

  @Test
  public void superGroupGetAllAlerts() {
    // given
    createSuperGroupAuthorization();
    grantSingleDefinitionAuthorizationsForGroup(GROUP_ID, PROCESS_DEFINITION_KEY);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser(PROCESS_DEFINITION_KEY, KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser(
      PROCESS_DEFINITION_KEY,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
    final String alertId1 = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    final String alertId2 = addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactlyInAnyOrder(alertId1, alertId2);
  }

  @Test
  public void superUserGetAllAlertsOnlyForAuthorizedDefinitions() {
    // given
    createSuperUserAuthorization();
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, PROCESS_DEFINITION_KEY);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser(PROCESS_DEFINITION_KEY, KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser(
      PROCESS_DEFINITION_KEY_2,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
    final String authorizedAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactly(authorizedAlertId);
  }

  @Test
  public void superGroupGetAllAlertsOnlyForAuthorizedDefinitions() {
    // given
    createSuperGroupAuthorization();
    grantSingleDefinitionAuthorizationsForGroup(GROUP_ID, PROCESS_DEFINITION_KEY);

    AlertCreationRequestDto alert1 = setupBasicProcessAlertAsUser(PROCESS_DEFINITION_KEY, KERMIT_USER, KERMIT_USER);
    AlertCreationRequestDto alert2 = setupBasicProcessAlertAsUser(
      PROCESS_DEFINITION_KEY_2,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
    final String authorizedAlertId = addAlertToOptimizeAsUser(alert1, KERMIT_USER, KERMIT_USER);
    addAlertToOptimizeAsUser(alert2, DEFAULT_USERNAME, DEFAULT_PASSWORD);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactly(authorizedAlertId);
  }

  @Test
  public void superUserGetAllAlertsOfCollectionReports() {
    // given
    createSuperUserAuthorization();

    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(PROCESS_DEFINITION_KEY);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, PROCESS_DEFINITION_KEY);

    importAllEngineEntitiesFromScratch();

    final String alertId = createAlertInCollectionAsDefaultUser(processDefinition);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactlyInAnyOrder(alertId);
  }

  @Test
  public void superGroupGetAllAlertsOfCollectionReports() {
    // given
    createSuperGroupAuthorization();

    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(PROCESS_DEFINITION_KEY);
    grantSingleDefinitionAuthorizationsForGroup(GROUP_ID, PROCESS_DEFINITION_KEY);

    importAllEngineEntitiesFromScratch();

    final String alertId = createAlertInCollectionAsDefaultUser(processDefinition);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactly(alertId);
  }

  @Test
  public void superUserGetAllAlertsOfCollectionReportsOnlyForAuthorizedDefinitions() {
    // given
    createSuperUserAuthorization();

    ProcessDefinitionEngineDto processDefinition1 = deploySimpleServiceTaskProcess(PROCESS_DEFINITION_KEY);
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, PROCESS_DEFINITION_KEY);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcess(PROCESS_DEFINITION_KEY_2);

    importAllEngineEntitiesFromScratch();

    final String authorizedAlertId = createAlertInCollectionAsDefaultUser(processDefinition1);
    createAlertInCollectionAsDefaultUser(processDefinition2);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactly(authorizedAlertId);
  }

  private void createSuperUserAuthorization() {
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.grantUserOptimizeAccess(KERMIT_USER);
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperUserIds(Collections.singletonList(KERMIT_USER));
  }

  @Test
  public void superGroupGetAllAlertsOfCollectionReportsOnlyForAuthorizedDefinitions() {
    // given
    createSuperGroupAuthorization();

    ProcessDefinitionEngineDto processDefinition1 = deploySimpleServiceTaskProcess(PROCESS_DEFINITION_KEY);
    grantSingleDefinitionAuthorizationsForGroup(GROUP_ID, PROCESS_DEFINITION_KEY);

    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcess(PROCESS_DEFINITION_KEY_2);

    importAllEngineEntitiesFromScratch();

    final String authorizedAlertId = createAlertInCollectionAsDefaultUser(processDefinition1);
    createAlertInCollectionAsDefaultUser(processDefinition2);

    // when
    List<AlertDefinitionDto> allAuthorizedAlerts = alertClient.getAllAlerts(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(allAuthorizedAlerts).extracting(AlertDefinitionDto::getId).containsExactly(authorizedAlertId);
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

  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = createAuthorizationDto(definitionKey);
    authorizationDto.setUserId(userId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private void grantSingleDefinitionAuthorizationsForGroup(String groupId, String definitionKey) {
    AuthorizationDto authorizationDto = createAuthorizationDto(definitionKey);
    authorizationDto.setGroupId(groupId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private AuthorizationDto createAuthorizationDto(final String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    return authorizationDto;
  }

  private void createSuperGroupAuthorization() {
    engineIntegrationExtension.addUser(KERMIT_USER, KERMIT_USER);
    engineIntegrationExtension.createGroup(GROUP_ID);
    engineIntegrationExtension.addUserToGroup(KERMIT_USER, GROUP_ID);
    engineIntegrationExtension.grantGroupOptimizeAccess(GROUP_ID);
    embeddedOptimizeExtension.getConfigurationService()
      .getAuthConfiguration()
      .setSuperGroupIds(Collections.singletonList(GROUP_ID));
  }

}
