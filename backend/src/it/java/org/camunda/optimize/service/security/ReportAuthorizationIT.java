/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
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
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessCountReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_TENANT;
import static org.camunda.optimize.test.util.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


@RunWith(JUnitParamsRunner.class)
public class ReportAuthorizationIT extends AbstractResourceAuthorizationIT {

  public static final String PROCESS_KEY = "aprocess";
  public static final String DECISION_KEY = "aDecision";

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  @Test
  @Parameters(method = "definitionType")
  public void evaluateUnauthorizedStoredReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void evaluateUnauthorizedTenantsStoredReport(int definitionResourceType) throws Exception {
    // given
    final String tenantId = "tenant1";
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForResource(definitionResourceType);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType, ImmutableList.of(tenantId));

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void evaluatePartiallyUnauthorizedTenantsStoredReport(int definitionResourceType) throws Exception {
    // given
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForResource(definitionResourceType);
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType, ImmutableList.of(tenantId1, tenantId2));

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void evaluateAllTenantsAuthorizedStoredReport(int definitionResourceType) throws Exception {
    // given
    final String tenantId1 = "tenant1";
    final String tenantId2 = "tenant2";
    addKermitUserAndGrantAccessToOptimize();
    addGlobalAuthorizationForResource(definitionResourceType);
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId1, RESOURCE_TYPE_TENANT);
    grantSingleResourceAuthorizationsForUser(KERMIT_USER, tenantId2, RESOURCE_TYPE_TENANT);
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(
      definitionResourceType,
      ImmutableList.of(tenantId1, tenantId2)
    );

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSavedReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  @Parameters(method = "definitionType")
  public void deleteUnauthorizedStoredReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildDeleteReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void evaluateUnauthorizedOnTheFlyReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    // when
    ReportDefinitionDto<SingleReportDataDto> definition = constructReportWithDefinition(definitionResourceType);
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildEvaluateSingleUnsavedReportRequest(definition.getData())
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void updateUnauthorizedReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    ReportDefinitionDto updatedReport = createReportUpdate(definitionResourceType);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateSingleReportRequest(reportId, updatedReport)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void getUnauthorizedReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void shareUnauthorizedReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(definitionResourceType);
    ReportShareDto reportShareDto = new ReportShareDto();
    reportShareDto.setReportId(reportId);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildShareReportRequest(reportShareDto)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

  @Test
  @Parameters(method = "definitionType")
  public void newReportCanBeAccessedByEveryone(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createNewReport(definitionResourceType);

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .execute();

    // then
    assertThat(response.getStatus(), is(200));

    // when
    List<ReportDefinitionDto> reports = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetReportRequest(reportId)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .executeAndReturnList(ReportDefinitionDto.class, 200);

    // then
    assertThat(reports.size(), is(1));
  }

  @Test
  public void unauthorizedReportInCombinedIsNotEvaluated() {
    // given
    final String authorizedProcessDefinitionKey = "aprocess";
    final String notAuthorizedProcessDefinitionKey = "notAuthorizedProcess";
    addKermitUserAndGrantAccessToOptimize();
    deployAndStartSimpleProcessDefinition(authorizedProcessDefinitionKey);
    grantSingleResourceAuthorizationForKermit(authorizedProcessDefinitionKey, RESOURCE_TYPE_PROCESS_DEFINITION);
    deployAndStartSimpleProcessDefinition(notAuthorizedProcessDefinitionKey);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    String authorizedReportId = createNewSingleMapReport(authorizedProcessDefinitionKey);
    String notAuthorizedReportId = createNewSingleMapReport(notAuthorizedProcessDefinitionKey);

    // when
    CombinedReportDataDto combinedReport = createCombinedReport(authorizedReportId, notAuthorizedReportId);

    CombinedProcessReportResultDataDto<ProcessCountReportMapResultDto> result = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<CombinedReportEvaluationResultDto<ProcessCountReportMapResultDto>>() {
      })
      .getResult();

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessCountReportMapResultDto>> resultMap = result.getData();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(notAuthorizedReportId), is(false));
    List<MapResultEntryDto<Long>> flowNodeToCount = resultMap.get(authorizedReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(2));
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }

  private String createNewSingleMapReport(String processDefinitionKey) {
    String singleReportId = createNewReport(RESOURCE_TYPE_PROCESS_DEFINITION);
    ProcessReportDataDto countFlowNodeFrequencyGroupByFlowNode =
      createCountFlowNodeFrequencyGroupByFlowNode(processDefinitionKey, "1");
    SingleProcessReportDefinitionDto definitionDto = new SingleProcessReportDefinitionDto();
    definitionDto.setData(countFlowNodeFrequencyGroupByFlowNode);
    updateReport(singleReportId, definitionDto);
    return singleReportId;
  }

  private void deployStartAndImportDefinition(int definitionResourceType) throws IOException {
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

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();
  }

  private void deployAndStartSimpleProcessDefinition(String processKey) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processKey)
      .startEvent()
      .endEvent()
      .done();
    engineRule.deployAndStartProcess(modelInstance);
  }

  private void deployAndStartSimpleDecisionDefinition(String decisionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(decisionKey);
    engineRule.deployAndStartDecisionDefinition(modelInstance);
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

  private String createReportForDefinition(int resourceType) {
    return createReportForDefinition(resourceType, Collections.emptyList());
  }

  private String createReportForDefinition(int resourceType, List<String> tenantIds) {
    String id = createNewReport(resourceType);
    ReportDefinitionDto definition = constructReportWithDefinition(resourceType, tenantIds);
    updateReport(id, definition);
    return id;
  }

  private String createNewReport(int resourceType) {
    switch (resourceType) {
      default:
      case RESOURCE_TYPE_PROCESS_DEFINITION:
        return embeddedOptimizeRule
          .getRequestExecutor()
          .buildCreateSingleProcessReportRequest()
          .execute(IdDto.class, 200)
          .getId();
      case RESOURCE_TYPE_DECISION_DEFINITION:
        return embeddedOptimizeRule
          .getRequestExecutor()
          .buildCreateSingleDecisionReportRequest()
          .execute(IdDto.class, 200)
          .getId();
    }
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
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
        ProcessReportDataDto processReportDataDto = createProcessReportDataViewRawAsTable(
          getDefinitionKey(resourceType),
          "1"
        );
        processReportDataDto.setTenantIds(tenantIds);
        processReportDefinitionDto.setData(processReportDataDto);
        return processReportDefinitionDto;
      case RESOURCE_TYPE_DECISION_DEFINITION:
        SingleDecisionReportDefinitionDto decisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
        DecisionReportDataDto decisionReportDataDto = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
          getDefinitionKey(resourceType), "1"
        );
        decisionReportDataDto.setTenantIds(tenantIds);
        decisionReportDefinitionDto.setData(decisionReportDataDto);
        return decisionReportDefinitionDto;
    }
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport) {
    switch (updatedReport.getReportType()) {
      default:
      case PROCESS:
        return embeddedOptimizeRule
          .getRequestExecutor()
          .buildUpdateSingleProcessReportRequest(id, (SingleProcessReportDefinitionDto) updatedReport)
          .execute();
      case DECISION:
        return embeddedOptimizeRule
          .getRequestExecutor()
          .buildUpdateSingleDecisionReportRequest(id, (SingleDecisionReportDefinitionDto) updatedReport)
          .execute();
    }
  }

}
