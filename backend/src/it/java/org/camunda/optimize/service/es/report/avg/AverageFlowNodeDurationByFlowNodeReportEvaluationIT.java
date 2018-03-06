package org.camunda.optimize.service.es.report.avg;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
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
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_AVERAGE_OPERATION;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.test.util.ReportDataHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;


public class AverageFlowNodeDurationByFlowNodeReportEvaluationIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  public static final String PROCESS_DEFINITION_KEY = "123";
  private static final String SERVICE_TASK_ID = "aSimpleServiceTask";
  private static final String SERVICE_TASK_ID_2 = "aSimpleServiceTask2";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule).around(engineDatabaseRule);

  @Test
  public void reportEvaluationForOneProcess() throws Exception {

    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    ReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_AVERAGE_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_FLOW_NODE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(20L));
    assertThat(flowNodeIdToAverageExecutionDuration.get(START_EVENT ), is(20L));
    assertThat(flowNodeIdToAverageExecutionDuration.get(END_EVENT ), is(20L));
  }

  @Test
  public void reportEvaluationForSeveralProcesses() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 10L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 30L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(20L));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 10L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 10L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 20L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData =
      getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(4));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(10L));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID_2 ), is(20L));
  }

  private ProcessDefinitionEngineDto deployProcessWithTwoTasks() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
        .camundaExpression("${true}")
      .serviceTask(SERVICE_TASK_ID_2)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() throws Exception {
    //given
    ProcessDefinitionEngineDto firstDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto latestDefinition = deployProcessWithTwoTasks();
    assertThat(latestDefinition.getVersion(), is(2));

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    ReportDataDto reportData = createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
        latestDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    MapReportResultDto result = evaluateReport(reportData);

    //then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(4));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID_2 ), is(40L));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() throws Exception {
    //given
    ProcessDefinitionEngineDto firstDefinition = deployProcessWithTwoTasks();
    ProcessDefinitionEngineDto latestDefinition = deploySimpleServiceTaskProcessDefinition();
    assertThat(latestDefinition.getVersion(), is(2));

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(firstDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(latestDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    ReportDataDto reportData = createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
        latestDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    MapReportResultDto result = evaluateReport(reportData);

    //then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    ReportDataDto reportData = createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
        processDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    MapReportResultDto result = evaluateReport(reportData);

    //then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData1 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapReportResultDto result1 = evaluateReport(reportData1);
    ReportDataDto reportData2 = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition2);
    MapReportResultDto result2 = evaluateReport(reportData2);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result1.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
    Map<String, Long> flowNodeIdToAverageExecutionDuration2 = result2.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration2.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration2.get(SERVICE_TASK_ID ), is(20L));
  }

  @Test
  public void evaluateReportWithIrrationalAverageNumberAsResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 100L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 300L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 600L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(333L));
  }

  @Test
  public void noEventMatchesReturnsEmptyResult() {

    // when
    ReportDataDto reportData =
      createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport("nonExistingProcessDefinitionId", "1");
    MapReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(0));
  }

  @Test
  public void processDefinitionContainsMultiInstanceBody() throws Exception {
    // given
    BpmnModelInstance subProcess = Bpmn.createExecutableProcess("subProcess")
        .startEvent()
          .serviceTask(SERVICE_TASK_ID)
            .camundaExpression("${true}")
        .endEvent()
        .done();

    BpmnModelInstance miProcess = Bpmn.createExecutableProcess("miProcess")
        .name("MultiInstance")
          .startEvent("miStart")
          .callActivity("callActivity")
            .calledElement("subProcess")
            .camundaIn("activityDuration", "activityDuration")
            .multiInstance()
              .cardinality("2")
            .multiInstanceDone()
          .endEvent("miEnd")
        .done();
    ProcessDefinitionEngineDto subProcessDefinition = engineRule.deployProcessAndGetProcessDefinition(subProcess);
    String processDefinitionId = engineRule.deployProcessAndGetId(miProcess);
    engineRule.startProcessInstance(processDefinitionId);
    engineDatabaseRule.changeActivityDurationForProcessDefinition(subProcessDefinition.getId(), 10L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData =
      createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(subProcessDefinition.getKey(), String.valueOf(subProcessDefinition.getVersion()));
    MapReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(10L));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();

    ProcessInstanceEngineDto processInstanceDto;
    for (int i = 0; i < 11; i++) {
      processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 10L);
    }
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToAverageExecutionDuration = result.getResult();
    assertThat(flowNodeIdToAverageExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToAverageExecutionDuration.get(SERVICE_TASK_ID ), is(10L));
  }

  @Test
  public void dateFilterInReport() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), 10L);
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createDateFilter("<", "start_date", past));
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(0));

    // when
    reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createDateFilter(">=", "start_date", past));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(SERVICE_TASK_ID ), is(10L));
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
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId(), variables);
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), 10L);
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(createVariableFilter());
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(SERVICE_TASK_ID ), is(10L));
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
    engineDatabaseRule.changeActivityDuration(processInstance.getId(), 10L);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    ReportDataDto reportData = getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    reportData.getFilter().addAll(flowNodeFilter);
    MapReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(5));
    assertThat(flowNodeIdToExecutionFrequency.get("task1" ), is(10L));
    assertThat(flowNodeIdToExecutionFrequency.get("task2" ), is(nullValue()));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ReportDataDto dataDto =
      createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ReportDataDto dataDto =
      createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ReportDataDto dataDto =
      createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
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

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcessDefinition() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess" )
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private MapReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return embeddedOptimizeRule.target("report/evaluate")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(Entity.json(reportData));
  }

  private ReportDataDto getAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(ProcessDefinitionEngineDto processDefinition) {
    return createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

}
