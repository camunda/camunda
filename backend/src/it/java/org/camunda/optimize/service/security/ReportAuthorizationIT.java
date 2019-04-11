/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import com.fasterxml.jackson.core.type.TypeReference;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_DECISION_DEFINITION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.util.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCombinedReport;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;


@RunWith(JUnitParamsRunner.class)
public class ReportAuthorizationIT {

  public static final String PROCESS_KEY = "aprocess";
  public static final String DECISION_KEY = "aDecision";
  public static final String KERMIT_USER = "kermit";

  private static final Object[] definitionType() {
    return new Object[]{RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION};
  }

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();

  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  @Parameters(method = "definitionType")
  public void evaluateUnauthorizedStoredReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(getDefinitionKey(definitionResourceType));

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
  public void deleteUnauthorizedStoredReport(int definitionResourceType) throws Exception {
    // given
    addKermitUserAndGrantAccessToOptimize();
    deployStartAndImportDefinition(definitionResourceType);

    String reportId = createReportForDefinition(getDefinitionKey(definitionResourceType));

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
    SingleProcessReportDefinitionDto definition = constructReportWithDefinition(
      getDefinitionKey(definitionResourceType)
    );
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

    String reportId = createReportForDefinition(getDefinitionKey(definitionResourceType));

    ReportDefinitionDto updatedReport = createReportUpdate();

    // when
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .buildUpdateReportRequest(reportId, updatedReport)
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

    String reportId = createReportForDefinition(getDefinitionKey(definitionResourceType));

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

    String reportId = createReportForDefinition(getDefinitionKey(definitionResourceType));
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

    String reportId = createNewReport();

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
    grantSingleDefinitionAuthorizationsForUser(KERMIT_USER, authorizedProcessDefinitionKey);
    deployAndStartSimpleProcessDefinition(notAuthorizedProcessDefinitionKey);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    String authorizedReportId = createNewSingleMapReport(authorizedProcessDefinitionKey);
    String notAuthorizedReportId = createNewSingleMapReport(notAuthorizedProcessDefinitionKey);

    // when
    CombinedReportDataDto combinedReport = createCombinedReport(authorizedReportId, notAuthorizedReportId);

    CombinedProcessReportResultDataDto<ProcessReportMapResultDto> result = embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateCombinedUnsavedReportRequest(combinedReport)
      .withUserAuthentication(KERMIT_USER, KERMIT_USER)
      .execute(new TypeReference<CombinedReportEvaluationResultDto<ProcessReportMapResultDto>>() {})
      .getResult();

    // then
    Map<String, ProcessReportEvaluationResultDto<ProcessReportMapResultDto>> resultMap = result.getData();
    assertThat(resultMap.size(), is(1));
    assertThat(resultMap.containsKey(notAuthorizedReportId), is(false));
    List<MapResultEntryDto<Long>> flowNodeToCount = resultMap.get(authorizedReportId).getResult().getData();
    assertThat(flowNodeToCount.size(), is(2));
  }

  private String getDefinitionKey(final int definitionResourceType) {
    return definitionResourceType == RESOURCE_TYPE_PROCESS_DEFINITION ? PROCESS_KEY : DECISION_KEY;
  }


  private void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

  private String createNewSingleMapReport(String processDefinitionKey) {
    String singleReportId = createNewReport();
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

  public ReportDefinitionDto createReportUpdate() {
    ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey("procdef");
    reportData.setProcessDefinitionVersion("123");
    reportData.setFilter(Collections.emptyList());
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setName("MyReport");
    return report;
  }

  private String createReportForDefinition(String definitionKey) {
    String id = createNewReport();
    ReportDefinitionDto definition = constructReportWithDefinition(definitionKey);
    updateReport(id, definition);
    return id;
  }

  public String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = getUpdateReportResponse(id, updatedReport);
    assertThat(response.getStatus(), is(204));
  }

  private SingleProcessReportDefinitionDto constructReportWithDefinition(String processDefinitionKey) {
    SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto data = createProcessReportDataViewRawAsTable(processDefinitionKey, "1");
    reportDefinitionDto.setData(data);
    return reportDefinitionDto;
  }

  private Response getUpdateReportResponse(String id, ReportDefinitionDto updatedReport) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateReportRequest(id, updatedReport)
      .execute();
  }

  private void addKermitUserAndGrantAccessToOptimize() {
    engineRule.addUser(KERMIT_USER, KERMIT_USER);
    engineRule.grantUserOptimizeAccess(KERMIT_USER);
  }

}
