package org.camunda.optimize.service.es.report.count;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractServiceTaskBuilder;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.definition.ExtendedProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.DateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.DateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.result.MapReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.hamcrest.core.Is;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_COUNT_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FREQUENCY_PROPERTY;
import static org.camunda.optimize.test.util.ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;


public class CountFlowNodeFrequencyByFlowNodeReportEvaluationIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  private static final String TEST_ACTIVITY = "testActivity";
  private static final String TEST_ACTIVITY_2 = "testActivity_2";

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() throws Exception {
    //given
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks(TEST_ACTIVITY);
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("2"));

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        latestProcess.getProcessDefinitionKey(), ALL_VERSIONS
    );
    MapReportResultDto result = evaluateReport(reportData);

    //then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(4));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(2L));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() throws Exception {
    //given
    deployProcessWithTwoTasks(TEST_ACTIVITY);
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("2"));

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        latestProcess.getProcessDefinitionKey(), ALL_VERSIONS
    );
    MapReportResultDto result = evaluateReport(reportData);

    //then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(2L));
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processInstanceDto.getProcessDefinitionKey(), ALL_VERSIONS
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_COUNT_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_FLOW_NODE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_FREQUENCY_PROPERTY));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(2L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_COUNT_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_FLOW_NODE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_FREQUENCY_PROPERTY));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(1L));
  }

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_COUNT_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_FLOW_NODE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_FREQUENCY_PROPERTY));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(1L));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion()
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(2L));
  }

  @Test
  public void evaluateReportForMultipleEventsWithMultipleProcesses() throws Exception {
    // given
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(instanceDto.getDefinitionId());

    ProcessInstanceEngineDto instanceDto2 = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData =
      createCountFlowNodeFrequencyGroupByFlowNode(instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion());
    MapReportResultDto result1 = evaluateReport(reportData);
    reportData.setProcessDefinitionKey(instanceDto2.getProcessDefinitionKey());
    reportData.setProcessDefinitionVersion(instanceDto2.getProcessDefinitionVersion());
    MapReportResultDto result2 = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto1 = result1.getData();
    assertThat(resultReportDataDto1.getProcessDefinitionKey(), is(instanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto1.getProcessDefinitionVersion(), is(instanceDto.getProcessDefinitionVersion()));
    assertThat(result1.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result1.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(2L));

    ReportDataDto resultReportDataDto2 = result2.getData();
    assertThat(resultReportDataDto2.getProcessDefinitionKey(), is(instanceDto2.getProcessDefinitionKey()));
    assertThat(resultReportDataDto2.getProcessDefinitionVersion(), is(instanceDto2.getProcessDefinitionVersion()));
    assertThat(result2.getResult(), is(notNullValue()));
    flowNodeIdToExecutionFrequency = result2.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(1L));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() throws Exception {
    // given
    AbstractServiceTaskBuilder serviceTaskBuilder = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask(TEST_ACTIVITY + 0)
      .camundaExpression("${true}");
    for (int i = 1; i < 11; i++) {
      serviceTaskBuilder = serviceTaskBuilder
        .serviceTask(TEST_ACTIVITY + i)
        .camundaExpression("${true}");
    }
    BpmnModelInstance processModel =
      serviceTaskBuilder.endEvent()
        .done();

    ProcessInstanceEngineDto instanceDto = engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion()
    );
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(13));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY + 0), is(1L));
  }

  @Test
  public void importWithMi() throws Exception {
    //given
    final String subProcessKey = "testProcess";
    final String callActivity = "callActivity";
    final String testMIProcess = "testMIProcess";

    BpmnModelInstance subProcess = Bpmn.createExecutableProcess(subProcessKey)
        .startEvent()
          .serviceTask("MI-Body-Task")
            .camundaExpression("${true}")
        .endEvent()
        .done();
    engineRule.deployProcessAndGetId(subProcess);

    BpmnModelInstance model = Bpmn.createExecutableProcess(testMIProcess)
        .name("MultiInstance")
          .startEvent("miStart")
          .parallelGateway()
            .endEvent("end1")
          .moveToLastGateway()
            .callActivity(callActivity)
            .calledElement(subProcessKey)
            .multiInstance()
              .cardinality("2")
            .multiInstanceDone()
          .endEvent("miEnd")
        .done();
    engineRule.deployAndStartProcess(model);

    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    List<ExtendedProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule.target()
        .path(embeddedOptimizeRule.getProcessDefinitionEndpoint())
        .request()
        .header(HttpHeaders.AUTHORIZATION,embeddedOptimizeRule.getAuthorizationHeader())
        .get(new GenericType<List<ExtendedProcessDefinitionOptimizeDto>>(){});
    assertThat(definitions.size(),is(2));

    //when
    ReportDataDto reportData =
      createCountFlowNodeFrequencyGroupByFlowNode(testMIProcess, "1");
    MapReportResultDto result = evaluateReport(reportData);

    //then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(5));
  }

  @Test
  public void dateFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(createDateFilter("<", "start_date", past));
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(0));

    // when
    reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(createDateFilter(">=", "start_date", past));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(1L));
  }

  public List<FilterDto> createDateFilter(String operator, String type, OffsetDateTime dateValue) {
    DateFilterDataDto date = new DateFilterDataDto();
    date.setOperator(operator);
    date.setType(type);
    date.setValue(dateValue);

    DateFilterDto dateFilterDto = new DateFilterDto();
    dateFilterDto.setData(date);
    return Collections.singletonList(dateFilterDto);
  }

  @Test
  public void variableFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcessWithVariables(variables);
    String processDefinitionId = processInstance.getDefinitionId();
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(createVariableFilter());
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY ), is(1L));
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
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion())
    );
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    reportData.getFilter().addAll(flowNodeFilter);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(5));
    assertThat(flowNodeIdToExecutionFrequency.get("task1" ), is(1L));
    assertThat(flowNodeIdToExecutionFrequency.get("task2" ), is(nullValue()));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ReportDataDto dataDto = createCountFlowNodeFrequencyGroupByFlowNode("123", "1");
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ReportDataDto dataDto = createCountFlowNodeFrequencyGroupByFlowNode("123","1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ReportDataDto dataDto = createCountFlowNodeFrequencyGroupByFlowNode("123", "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasks(String activityId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
      .serviceTask(activityId)
        .camundaExpression("${true}")
      .serviceTask(TEST_ACTIVITY_2)
        .camundaExpression("${true}")
      .endEvent("end")
      .done();
    return engineRule.deployAndStartProcess(modelInstance);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess(String activityId) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
        .serviceTask(activityId)
        .camundaExpression("${true}")
      .endEvent("end")
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(Map<String, Object> variables) {
    return deployAndStartSimpleServiceTaskProcessWithVariables(TEST_ACTIVITY, variables);
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcessWithVariables(String activityId,
                                                                                       Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
        .serviceTask(activityId)
        .camundaExpression("${true}")
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

  private MapReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .post(Entity.json(reportData));
  }


}
