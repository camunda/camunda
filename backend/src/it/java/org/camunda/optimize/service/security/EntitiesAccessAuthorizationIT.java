/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

@RunWith(JUnitParamsRunner.class)
public class EntitiesAccessAuthorizationIT extends AbstractCollectionRoleIT {

  @Test
  @Parameters(method = ACCESS_IDENTITY_ROLES)
  public void containsAuthorizedCollectionsByCollectionUserRole(final IdentityAndRole accessIdentityRolePairs) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.createKermitGroupAndAddKermitToThatGroup();

    final String collectionId = createNewCollectionAsDefaultUser();
    addRoleToCollectionAsDefaultUser(
      accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId
    );

    // when
    final List<EntityDto> authorizedEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(authorizedEntities.size(), is(1));
    assertThat(
      authorizedEntities.stream().map(EntityDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(collectionId)
    );
    assertThat(
      authorizedEntities.stream().map(EntityDto::getCurrentUserRole).collect(Collectors.toList()),
      contains(accessIdentityRolePairs.roleType)
    );
  }

  @Test
  public void superUserAllEntitiesAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    final String collectionId = createNewCollectionAsDefaultUser();
    final String combinedReportId = createCombinedReportAsDefaultUser();
    final String processReportId = createSingleProcessReportAsDefaultUser();
    final String decisionReportId = createSingleDecisionReportAsDefaultUser();
    final String dashboardId = createDashboardAsDefaultUser();

    // when
    final List<EntityDto> authorizedEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(authorizedEntities.size(), is(5));
    assertThat(
      authorizedEntities.stream().map(EntityDto::getId).collect(Collectors.toList()),
      containsInAnyOrder(collectionId, combinedReportId, processReportId, decisionReportId, dashboardId)
    );
    assertThat(
      authorizedEntities.stream().map(EntityDto::getCurrentUserRole).collect(Collectors.toList()),
      everyItem(greaterThanOrEqualTo(RoleType.EDITOR))
    );
  }

  @Test
  public void superUserEntitiesNotAuthorizedForDefinitionAreHidden() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeRule.getConfigurationService().getSuperUserIds().add(KERMIT_USER);

    ProcessDefinitionEngineDto unauthorizedProcess = deploySimpleServiceTaskProcess("unauthorizedProcess");
    DecisionDefinitionEngineDto unauthorizedDecision = deploySimpleDecisionDefinition("unauthorizedDecision");

    createSingleProcessReportForDefinitionAsDefaultUser(unauthorizedProcess);
    createSingleDecisionReportForDefinitionAsDefaultUser(unauthorizedDecision);

    // when
    final List<EntityDto> authorizedEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(authorizedEntities.size(), is(0));
  }

  @Test
  public void unauthorizedCollectionAndOtherUsersPrivateItemsNotAvailable() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    createNewCollectionAsDefaultUser();
    createCombinedReportAsDefaultUser();
    createSingleProcessReportAsDefaultUser();
    createSingleDecisionReportAsDefaultUser();
    createDashboardAsDefaultUser();

    // when
    final List<EntityDto> authorizedEntities = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetAllEntitiesRequest()
      .executeAndReturnList(EntityDto.class, 200);

    // then
    assertThat(authorizedEntities.size(), is(0));
  }

  private String createDashboardAsDefaultUser() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createSingleDecisionReportForDefinitionAsDefaultUser(final DecisionDefinitionEngineDto decisionDefinition) {
    final DecisionReportDataDto reportDataDto = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinition.getKey())
      .setDecisionDefinitionVersion(decisionDefinition.getVersionAsString())
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();

    final String singleReportId = createSingleDecisionReportAsDefaultUser();

    final SingleDecisionReportDefinitionDto definitionDto = new SingleDecisionReportDefinitionDto();
    definitionDto.setData(reportDataDto);
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleDecisionReportRequest(singleReportId, definitionDto)
      .execute();

    return singleReportId;
  }

  private String createSingleDecisionReportAsDefaultUser() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createSingleProcessReportForDefinitionAsDefaultUser(final ProcessDefinitionEngineDto processDefinition) {
    final ProcessReportDataDto reportDataDto = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setDateInterval(GroupByDateUnit.AUTOMATIC)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE)
      .build();

    final String singleReportId = createSingleProcessReportAsDefaultUser();

    final SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(reportDataDto);
    embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(singleReportId, definitionDto)
      .execute();

    return singleReportId;
  }

  private String createSingleProcessReportAsDefaultUser() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createCombinedReportAsDefaultUser() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateCombinedReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(final String definitionKey) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(processModel);
  }

  protected DecisionDefinitionEngineDto deploySimpleDecisionDefinition(final String definitionKey) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .decisionDefinitionKey(definitionKey)
      .addInput("input", "input", "input", DecisionTypeRef.STRING)
      .addOutput("output", DecisionTypeRef.STRING)
      .buildDecision();
    return engineRule.deployDecisionDefinition(dmnModelGenerator.build());
  }

}
