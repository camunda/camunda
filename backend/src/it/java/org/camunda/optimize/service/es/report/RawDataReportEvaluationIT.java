package org.camunda.optimize.service.es.report;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.flownode.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ViewDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableFilterDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.report.ReportEvaluationManager.RAW_DATA_OPERATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class RawDataReportEvaluationIT {


  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void reportEvaluationForOneProcessInstance() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getView(), is(notNullValue()));
    assertThat(result.getView().getOperation(), is(RAW_DATA_OPERATION));
    assertThat(result.getRawData(), is(notNullValue()));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is("aProcess"));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationByIdForOneProcessInstance() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createAndStoreDefaultReportDefinition(processDefinitionId);

    // when
    RawDataReportResultDto result = evaluateReportById(reportId);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getView(), is(notNullValue()));
    assertThat(result.getView().getOperation(), is(RAW_DATA_OPERATION));
    assertThat(result.getRawData(), is(notNullValue()));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is("aProcess"));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionId) {
    String id = createNewReport();
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    LocalDateTime someDate = LocalDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  @Test
  public void reportEvaluationWithSeveralProcessInstances() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    ProcessInstanceEngineDto processInstance2 = engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getView(), is(notNullValue()));
    assertThat(result.getView().getOperation(), is(RAW_DATA_OPERATION));
    assertThat(result.getRawData(), is(notNullValue()));
    assertThat(result.getRawData().size(), is(2));
    Set<String> expectedProcessInstanceIds = new HashSet<>();
    expectedProcessInstanceIds.add(processInstance.getId());
    expectedProcessInstanceIds.add(processInstance2.getId());
    for (RawDataProcessInstanceDto rawDataProcessInstanceDto : result.getRawData()) {
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processDefinitionId));
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is("aProcess"));
      String actualProcessInstanceId = rawDataProcessInstanceDto.getProcessInstanceId();
      assertThat(expectedProcessInstanceIds.contains(actualProcessInstanceId), is(true));
      expectedProcessInstanceIds.remove(actualProcessInstanceId);
    }
  }

  @Test
  public void reportEvaluationOnProcessInstanceWithAllVariableTypes() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "Hello World!");
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 2);
    variables.put("longVar", "Hello World!");
    variables.put("dateVar", new Date());

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getView(), is(notNullValue()));
    assertThat(result.getView().getOperation(), is(RAW_DATA_OPERATION));
    assertThat(result.getRawData(), is(notNullValue()));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processDefinitionId));
    rawDataProcessInstanceDto.getVariables().
      forEach(var -> {
          assertThat(variables.keySet().contains(var.getName()), is(true));
          assertThat(variables.get(var.getName()), is(notNullValue()));
        }
      );
  }

  @Test
  public void evaluationReturnsOnlyDataToGivenProcessDefinitionId() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processDefinitionId));
  }

  @Test
  public void dateFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    Date past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    reportData.setFilter(createDateFilter("<", "start_date", past));
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getView(), is(notNullValue()));
    assertThat(result.getView().getOperation(), is(RAW_DATA_OPERATION));
    assertThat(result.getRawData(), is(notNullValue()));
    assertThat(result.getRawData().size(), is(0));

    // when
    reportData = new ReportDataDto();
    reportData.setProcessDefinitionId(processDefinitionId);
    reportData.setVisualization("table");
    reportData.setView(new ViewDto(RAW_DATA_OPERATION, null));
    reportData.setFilter(createDateFilter(">=", "start_date", past));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getView(), is(notNullValue()));
    assertThat(result.getView().getOperation(), is(RAW_DATA_OPERATION));
    assertThat(result.getRawData(), is(notNullValue()));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  private ReportDataDto createDefaultReportData(String processDefinitionId) {
    ReportDataDto reportData = new ReportDataDto();
    reportData.setProcessDefinitionId(processDefinitionId);
    reportData.setVisualization("table");
    reportData.setView(new ViewDto(RAW_DATA_OPERATION, null));
    return reportData;
  }

  public FilterMapDto createDateFilter(String operator, String type, Date dateValue) {
    FilterMapDto filter ;
    filter = new FilterMapDto();
    filter.setDates(new ArrayList<>());

    DateFilterDto date = new DateFilterDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);
    filter.getDates().add(date);
    return filter;
  }

  @Test
  public void variableFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);
    String processDefinitionId = processInstance.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    reportData.setFilter(createVariableFilter("var"));
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  private FilterMapDto createVariableFilter(String variableName) {
    VariableFilterDto filter = new VariableFilterDto();
    filter.setName(variableName);
    filter.setType("boolean");
    filter.setOperator("=");
    filter.setValues(Collections.singletonList("true"));
    FilterMapDto filterMapDto = new FilterMapDto();
    filterMapDto.setVariables(Collections.singletonList(filter));
    return filterMapDto;
  }

  @Test
  public void flowNodeFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinitionId, variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createDefaultReportData(processDefinitionId);
    FilterMapDto mapDto = new FilterMapDto();
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    mapDto.setExecutedFlowNodes(flowNodeFilter);
    reportData.setFilter(mapDto);
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getProcessDefinitionId(), is(processDefinitionId));
    assertThat(result.getRawData().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getRawData().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    //when
    Response response = evaluateReportAndReturnResponse(null);

    // then
    assertThat(response.getStatus() ,is(500));
  }

  @Test
  public void missingProcessDefinition() {

    //when
    ReportDataDto dataDto = createDefaultReportData(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus() ,is(500));
  }

  @Test
  public void missingViewField() {
    //when
    ReportDataDto dataDto = createDefaultReportData(null);
    dataDto.setView(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingOperationField() throws Exception {
    //when
    ReportDataDto dataDto = createDefaultReportData(null);
    dataDto.getView().setOperation(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingVisualizationField() throws Exception {
    //when
    ReportDataDto dataDto = createDefaultReportData(null);
    dataDto.setVisualization(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private String deploySimpleGatewayProcessDefinition() throws Exception {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask("task1")
          .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
        .endEvent("endEvent")
      .moveToNode("splittingGateway")
        .condition("no", "${!goToTask1}")
        .serviceTask("task2")
          .camundaExpression("${true}")
        .connectTo("mergeGateway")
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  private RawDataReportResultDto evaluateReportById(String reportId) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response = embeddedOptimizeRule.target("report/" + reportId + "/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .get();
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(Entity.json(reportData));
  }


  private String createNewReport() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }
}
