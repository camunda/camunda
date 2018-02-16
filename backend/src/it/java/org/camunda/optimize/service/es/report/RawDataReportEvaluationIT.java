package org.camunda.optimize.service.es.report;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class RawDataReportEvaluationIT {


  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  @Test
  public void reportEvaluationForOneProcessInstance() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
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

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createAndStoreDefaultReportDefinition(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());

    // when
    RawDataReportResultDto result = evaluateReportById(reportId);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    String id = createNewReport();
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
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

    ProcessInstanceEngineDto processInstance2 = engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));
    Set<String> expectedProcessInstanceIds = new HashSet<>();
    expectedProcessInstanceIds.add(processInstance.getId());
    expectedProcessInstanceIds.add(processInstance2.getId());
    for (RawDataProcessInstanceDto rawDataProcessInstanceDto : result.getResult()) {
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
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

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    rawDataProcessInstanceDto.getVariables().
      forEach((varName, varValue) -> {
          assertThat(variables.keySet().contains(varName), is(true));
          assertThat(variables.get(varName), is(notNullValue()));
        }
      );
  }

  @Test
  public void resultShouldBeOrderAccordingToStartDate() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    ProcessInstanceEngineDto processInstanceDto2 =  engineRule.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(),processInstance.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getResult();
    assertThat(rawDataList, isInDescendingOrdering());
  }

  private Matcher<? super List<RawDataProcessInstanceDto>> isInDescendingOrdering()
  {
    return new TypeSafeMatcher<List<RawDataProcessInstanceDto>>()
    {
      @Override
      public void describeTo (Description description)
      {
        description.appendText("The given list should be sorted in ascending order!");
      }

      @Override
      protected boolean matchesSafely (List<RawDataProcessInstanceDto> items)
      {
        for(int i = (items.size()-1) ; i > 0 ; i--) {
          if(items.get(i).getStartDate().isAfter(items.get(i-1).getStartDate())) return false;
        }
        return true;
      }
    };
  }

  @Test
  public void variablesOfOneProcessInstanceAreAddedToOther() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("varName1", "value1");

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    variables.clear();
    variables.put("varName2", "value2");
    engineRule.startProcessInstance(processInstance.getDefinitionId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(),processInstance.getProcessDefinitionVersion());
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));
    result.getResult().forEach(
      rawDataProcessInstanceDto1 -> {
        Map<String, Object> vars = rawDataProcessInstanceDto1.getVariables();
        assertThat(vars.keySet().size(), is(2));
        assertThat(vars.values().contains(""), is(true));
        // ensure is ordered
        List<String> actual = new ArrayList<>(vars.keySet());
        List<String> expected = new ArrayList<>(vars.keySet());
        Collections.sort(expected);
        assertThat(actual, contains(expected.toArray()));
      }
    );
  }

  @Test
  public void evaluationReturnsOnlyDataToGivenProcessDefinitionId() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
  }

  //test that basic support for filter is there
  @Test
  public void durationFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDurationFilter(">", 1, "Days"));
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));

    // when
    reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDurationFilter("<", 1, "Days"));
    result = evaluateReport(reportData);

    // then
    resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }


  @Test
  public void dateFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDateFilter("<", "start_date", past));
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));

    // when
    reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDateFilter(">=", "start_date", past));
    result = evaluateReport(reportData);

    // then
    resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void variableFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(createVariableFilter());
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  private List<FilterDto> createVariableFilter() {
    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("var");
    data.setType("boolean");
    data.setOperator("=");
    data.setValues(Collections.singletonList("true"));

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void flowNodeFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    reportData.getFilter().addAll(flowNodeFilter);
    RawDataReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
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
    ReportDataDto dataDto = ReportDataHelper.createReportDataViewRawAsTable(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus() ,is(500));
  }

  @Test
  public void missingViewField() {
    //when
    ReportDataDto dataDto = ReportDataHelper.createReportDataViewRawAsTable(null);
    dataDto.setView(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingOperationField() throws Exception {
    //when
    ReportDataDto dataDto = ReportDataHelper.createReportDataViewRawAsTable(null);
    dataDto.getView().setOperation(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingVisualizationField() throws Exception {
    //when
    ReportDataDto dataDto = ReportDataHelper.createReportDataViewRawAsTable(null);
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

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() throws Exception {
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
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
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
