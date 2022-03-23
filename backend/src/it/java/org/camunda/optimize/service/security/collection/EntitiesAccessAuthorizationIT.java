/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security.collection;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.security.CaseInsensitiveAuthenticationMockUtil;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.GROUP_ID;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class EntitiesAccessAuthorizationIT extends AbstractCollectionRoleIT {

  @ParameterizedTest
  @MethodSource(ACCESS_IDENTITY_ROLES)
  public void containsAuthorizedCollectionsByCollectionUserRole(final IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(authorizedEntities)
      .singleElement()
      .satisfies(entity -> {
        assertThat(entity.getId().equals(collectionId));
        assertThat(entity.getCurrentUserRole().equals(accessIdentityRolePairs.getRoleType()));
      });
  }

  @Test
  public void collectionAccessByRoleDoesNotDependOnUsernameCaseAtLoginWithCaseInsensitiveAuthenticationBackend() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String allUpperCaseUserId = KERMIT_USER.toUpperCase();
    final String actualUserId = KERMIT_USER;
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();

    final List<HttpRequest> mockedRequests = CaseInsensitiveAuthenticationMockUtil.setupCaseInsensitiveAuthentication(
      embeddedOptimizeExtension, engineIntegrationExtension, engineMockServer,
      allUpperCaseUserId, actualUserId
    );

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    addRoleToCollectionAsDefaultUser(
      RoleType.VIEWER, new UserDto(actualUserId), collectionId
    );

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(
      allUpperCaseUserId,
      actualUserId
    );

    // then
    assertThat(authorizedEntities)
      .singleElement()
      .satisfies(entity -> assertThat(entity.getId().equals(collectionId)));

    mockedRequests.forEach(engineMockServer::verify);
  }

  @Test
  public void superUserAllEntitiesAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String combinedReportId = reportClient.createEmptyCombinedReport(null);
    final String processReportId =
      reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto());
    final String decisionReportId =
      reportClient.createSingleDecisionReport(new SingleDecisionReportDefinitionRequestDto());
    final String dashboardId = dashboardClient.createDashboard(null);

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(authorizedEntities)
      .hasSize(5)
      .allSatisfy(response -> assertThat(response.getCurrentUserRole()).isGreaterThanOrEqualTo(RoleType.EDITOR))
      .extracting(EntityResponseDto::getId)
      .containsExactlyInAnyOrder(collectionId, combinedReportId, processReportId, decisionReportId, dashboardId);
  }

  @Test
  public void superGroupAllEntitiesAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
    final String combinedReportId = reportClient.createEmptyCombinedReport(null);
    final String processReportId =
      reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto());
    final String decisionReportId =
      reportClient.createSingleDecisionReport(new SingleDecisionReportDefinitionRequestDto());
    final String dashboardId = dashboardClient.createDashboard(null);

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(authorizedEntities)
      .hasSize(5)
      .allSatisfy(response -> assertThat(response.getCurrentUserRole()).isGreaterThanOrEqualTo(RoleType.EDITOR))
      .extracting(EntityResponseDto::getId)
      .containsExactlyInAnyOrder(collectionId, combinedReportId, processReportId, decisionReportId, dashboardId);
  }

  @Test
  public void superUserEntitiesNotAuthorizedForDefinitionAreHidden() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);

    ProcessDefinitionEngineDto unauthorizedProcess = deploySimpleServiceTaskProcess("unauthorizedProcess");
    DecisionDefinitionEngineDto unauthorizedDecision = deploySimpleDecisionDefinition("unauthorizedDecision");

    reportClient.createAndStoreProcessReport(unauthorizedProcess.getKey());
    reportClient.createSingleDecisionReportDefinitionDto(unauthorizedDecision.getKey()).getId();

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(authorizedEntities).isEmpty();
  }

  @Test
  public void superGroupEntitiesNotAuthorizedForDefinitionAreHidden() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().setSuperGroupIds(Collections.singletonList(GROUP_ID));

    ProcessDefinitionEngineDto unauthorizedProcess = deploySimpleServiceTaskProcess("unauthorizedProcess");
    DecisionDefinitionEngineDto unauthorizedDecision = deploySimpleDecisionDefinition("unauthorizedDecision");

    reportClient.createAndStoreProcessReport(unauthorizedProcess.getKey());
    reportClient.createSingleDecisionReportDefinitionDto(unauthorizedDecision.getKey()).getId();

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(authorizedEntities).isEmpty();
  }

  @Test
  public void unauthorizedCollectionAndOtherUsersPrivateItemsNotAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    collectionClient.createNewCollectionForAllDefinitionTypes();
    reportClient.createEmptyCombinedReport(null);
    reportClient.createSingleProcessReport(new SingleProcessReportDefinitionRequestDto());
    reportClient.createSingleDecisionReport(new SingleDecisionReportDefinitionRequestDto());
    dashboardClient.createDashboard(null);

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(KERMIT_USER, KERMIT_USER);

    // then
    assertThat(authorizedEntities).isEmpty();
  }

  @Test
  public void privateEntitiesVisibilityDoesNotDependOnUsernameCaseAtLoginWithCaseInsensitiveAuthenticationBackend() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();
    authorizationClient.grantKermitGroupOptimizeAccess();

    final String allUpperCaseUserId = KERMIT_USER.toUpperCase();
    final String actualUserId = KERMIT_USER;
    final ClientAndServer engineMockServer = useAndGetEngineMockServer();

    final List<HttpRequest> mockedRequests = CaseInsensitiveAuthenticationMockUtil.setupCaseInsensitiveAuthentication(
      embeddedOptimizeExtension, engineIntegrationExtension, engineMockServer,
      allUpperCaseUserId, actualUserId
    );

    collectionClient.createNewCollection(actualUserId, actualUserId);
    reportClient.createSingleProcessReportAsUserAndReturnResponse(null, null, actualUserId, actualUserId);
    reportClient.createSingleDecisionReportAsUser(null, null, actualUserId, actualUserId);
    reportClient.createNewCombinedReportAsUserRawResponse(null, Collections.emptyList(), actualUserId, actualUserId);
    dashboardClient.createDashboardAsUser(null, actualUserId, actualUserId);

    // when
    final List<EntityResponseDto> authorizedEntities = entitiesClient.getAllEntitiesAsUser(
      allUpperCaseUserId,
      actualUserId
    );

    // then
    assertThat(authorizedEntities)
      .hasSize(5)
      .allSatisfy(response -> assertThat(response.getCurrentUserRole()).isGreaterThanOrEqualTo(RoleType.EDITOR));

    mockedRequests.forEach(engineMockServer::verify);
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(final String definitionKey) {
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      BpmnModels.getSingleServiceTaskProcess(definitionKey)
    );
  }

  protected DecisionDefinitionEngineDto deploySimpleDecisionDefinition(final String definitionKey) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .decisionDefinitionKey(definitionKey)
      .addInput("input", "input", "input", DecisionTypeRef.STRING)
      .addOutput("output", DecisionTypeRef.STRING)
      .buildDecision();
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelGenerator.build());
  }

}
