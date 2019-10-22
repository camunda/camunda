/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedCombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.test.engine.AuthorizationClient;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ReportDefinitionAuthorizationIT {

  private static final String PROCESS_KEY = "aprocess";
  private static final String DECISION_KEY = "aDecision";

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  private AuthorizationClient authorizationClient = new AuthorizationClient(engineIntegrationExtensionRule);

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedStoredReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedTenantsStoredReport(int definitionResourceType) {
    // given
    final String tenantId = "tenant1";
    engineIntegrationExtensionRule.createTenant(tenantId);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType, ImmutableList.of(tenantId));

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluatePartiallyUnauthorizedTenantsStoredReport(int definitionResourceType) {
    // given
    final String tenantId1 = "tenant1";
    engineIntegrationExtensionRule.createTenant(tenantId1);
    final String tenantId2 = "tenant2";
    engineIntegrationExtensionRule.createTenant(tenantId2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType, ImmutableList.of(tenantId1, tenantId2));

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateAllTenantsAuthorizedStoredReport(int definitionResourceType) {
    // given
    final String tenantId1 = "tenant1";
    engineIntegrationExtensionRule.createTenant(tenantId1);
    final String tenantId2 = "tenant2";
    engineIntegrationExtensionRule.createTenant(tenantId2);

    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    authorizationClient.grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinitionAsUser(
      definitionResourceType, ImmutableList.of(tenantId1, tenantId2), KERMIT_USER, KERMIT_USER
    );

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void deleteUnauthorizedStoredReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void evaluateUnauthorizedOnTheFlyReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    // when
    ReportDefinitionDto<SingleReportDataDto> definition = constructReportWithDefinition(definitionResourceType);
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(definition.getData())
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void updateUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    ReportDefinitionDto updatedReport = createReportUpdate(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateSingleReportRequest(reportId, updatedReport)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void getUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void shareUnauthorizedReport(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);
    ReportShareDto reportShareDto = new ReportShareDto();
    reportShareDto.setReportId(reportId);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildShareReportRequest(reportShareDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @ParameterizedTest
  @MethodSource("definitionType")
  public void newPrivateReportsCanOnlyBeAccessedByOwner(int definitionResourceType) {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createNewReport(definitionResourceType);

    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    // when
    Response otherUserResponse = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(otherUserResponse.getStatus(), is(403));
  }

  @Test
  public void unauthorizedReportInCombinedIsNotEvaluated() {
    // given
    final String authorizedProcessDefinitionKey = "aprocess";
    final String notAuthorizedProcessDefinitionKey = "notAuthorizedProcess";
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition(authorizedProcessDefinitionKey);
    authorizationClient.grantSingleResourceAuthorizationForKermit(
      authorizedProcessDefinitionKey,
      RESOURCE_TYPE_PROCESS_DEFINITION
    );
    deployAndStartSimpleProcessDefinition(notAuthorizedProcessDefinitionKey);
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    String authorizedReportId = createNewSingleMapReportAsUser(
      authorizedProcessDefinitionKey, KERMIT_USER, KERMIT_USER
    );
    String notAuthorizedReportId = createNewSingleMapReportAsUser(
      notAuthorizedProcessDefinitionKey, KERMIT_USER, KERMIT_USER
    );

    // when
    CombinedReportDataDto combinedReport = createCombinedReport(authorizedReportId, notAuthorizedReportId);

    CombinedProcessReportResultDataDto<ReportMapResultDto> result = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<AuthorizedCombinedReportEvaluationResultDto<ReportMapResultDto>>() {
      })
      .getResult();

    // then
    Map<String, AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto>> resultMap = result.getData();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(notAuthorizedReportId), is(false));
    List<MapResultEntryDto> flowNodeToCount = resultMap.get(authorizedReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(2));
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  private String createNewSingleMapReportAsUser(final String processDefinitionKey,
                                                final String user,
                                                final String password) {
    String singleReportId = createNewReportAsUser(RESOURCE_TYPE_PROCESS_DEFINITION, user, password);
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion("1")
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReportAsUser(singleReportId, definitionDto, user, password);
    return singleReportId;
  }

  private void deployStartAndImportDefinition(int definitionResourceType) {
    switch (definitionResourceType) {
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        deployAndStartSimpleProcessDefinition(PROCESS_KEY);
        break;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        deployAndStartSimpleDecisionDefinition(DECISION_KEY);
        break;
      default:
        throw new IllegalStateException("Uncovered definitionResourceType: " + definitionResourceType);
    }

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
  }

  private void deployAndStartSimpleProcessDefinition(String processKey) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processKey)
      .startEvent()
      .endEvent()
      .done();
    engineIntegrationExtensionRule.deployAndStartProcess(modelInstance);
  }

  private void deployAndStartSimpleDecisionDefinition(String decisionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    engineIntegrationExtensionRule.deployAndStartDecisionDefinition(modelInstance);
  }

  private ReportDefinitionDto createReportUpdate(int definitionResourceType) {
    switch (definitionResourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        ProcessReportDataDto processReportData = new ProcessReportDataDto();
        processReportData.setProcessDefinitionKey("procdef");
        processReportData.setProcessDefinitionVersion("123");
        processReportData.setFilter(Collections.emptyList());
        SingleProcessReportDefinitionDto processReport = new SingleProcessReportDefinitionDto();
        processReport.setData(processReportData);
        processReport.setName("MyReport");
        return processReport;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        DecisionReportDataDto decisionReportData = new DecisionReportDataDto();
        decisionReportData.setDecisionDefinitionKey("Decisionef");
        decisionReportData.setDecisionDefinitionVersion("123");
        decisionReportData.setFilter(Collections.emptyList());
        SingleDecisionReportDefinitionDto decisionReport = new SingleDecisionReportDefinitionDto();
        decisionReport.setData(decisionReportData);
        decisionReport.setName("MyReport");
        return decisionReport;
    }
  }

  private String createReportForDefinition(final int resourceType) {
    return createReportForDefinition(resourceType, Collections.emptyList());
  }

  private String createReportForDefinition(final int resourceType, final List<String> tenantIds) {
    return createReportForDefinitionAsUser(resourceType, tenantIds, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createReportForDefinitionAsUser(final int resourceType,
                                                 final List<String> tenantIds,
                                                 final String user,
                                                 final String password) {
    String id = createNewReportAsUser(resourceType, user, password);
    ReportDefinitionDto definition = constructReportWithDefinition(resourceType, tenantIds);
    updateReportAsUser(id, definition, user, password);
    return id;
  }

  private String createNewReport(int resourceType) {
    return createNewReportAsUser(resourceType, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  private String createNewReportAsUser(int resourceType, final String user, final String password) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildCreateSingleProcessReportRequest()
          .execute(IdDto.class, 200)
          .getId();
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildCreateSingleDecisionReportRequest()
          .execute(IdDto.class, 200)
          .getId();
    }
  }

  private void updateReportAsUser(String id, ReportDefinitionDto updatedReport, final String user,
                                  final String password) {
    Response response = getUpdateReportResponse(id, updatedReport, user, password);
    assertThat(response.getStatus(), is(204));
  }

  private ReportDefinitionDto constructReportWithDefinition(int resourceType) {
    return constructReportWithDefinition(resourceType, Collections.emptyList());
  }

  private ReportDefinitionDto constructReportWithDefinition(int resourceType, List<String> tenantIds) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        SingleProcessReportDefinitionDto processReportDefinitionDto = new SingleProcessReportDefinitionDto();
        ProcessReportDataDto processReportDataDto = ProcessReportDataBuilder
          .createReportData()
          .setProcessDefinitionKey(getDefinitionKey(resourceType))
          .setProcessDefinitionVersion("1")
          .setReportDataType(ProcessReportDataType.RAW_DATA)
          .build();
        processReportDataDto.setTenantIds(tenantIds);
        processReportDefinitionDto.setData(processReportDataDto);
        return processReportDefinitionDto;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        SingleDecisionReportDefinitionDto decisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder.create()
          .setDecisionDefinitionKey(getDefinitionKey(resourceType))
          .setDecisionDefinitionVersion("1")
          .setReportDataType(DecisionReportDataType.RAW_DATA)
          .build();
        decisionReportDataDto.setTenantIds(tenantIds);
        decisionReportDefinitionDto.setData(decisionReportDataDto);
        return decisionReportDefinitionDto;
    }
  }

  private Response getUpdateReportResponse(final String id,
                                           final ReportDefinitionDto updatedReport,
                                           final String user,
                                           final String password) {
    switch (updatedReport.getReportType()) {
      default:
      case PROCESS:
        return embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildUpdateSingleProcessReportRequest(id, updatedReport)
          .execute();
      case DECISION:
        return embeddedOptimizeExtensionRule
          .getRequestExecutor()
          .withUserAuthentication(user, password)
          .buildUpdateSingleDecisionReportRequest(id, updatedReport)
          .execute();
    }
  }

}
