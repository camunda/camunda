package org.camunda.optimize.service.es.report.flownode.duration;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_DURATION_PROPERTY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_FLOW_NODE_ENTITY;
import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_MAX_OPERATION;
import static org.camunda.optimize.test.util.ReportDataBuilderHelper
  .createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;


public class MaxFlowNodeDurationByFlowNodeReportEvaluationIT {

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
    SingleReportDataDto reportData = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultReportDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getOperation(), is(VIEW_MAX_OPERATION));
    assertThat(resultReportDataDto.getView().getEntity(), is(VIEW_FLOW_NODE_ENTITY));
    assertThat(resultReportDataDto.getView().getProperty(), is(VIEW_DURATION_PROPERTY));
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(20L));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(START_EVENT ), is(20L));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(END_EVENT ), is(20L));
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
    SingleReportDataDto reportData = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(30L));
  }

  @Test
  public void evaluateReportForMultipleEvents() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWithTwoTasks();

    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 100L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID, 10L);
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), SERVICE_TASK_ID_2, 200L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData =
      getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(4));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(100L));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID_2 ), is(200L));
  }

  private ProcessDefinitionEngineDto deployProcessWithTwoTasks() {
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
    SingleReportDataDto reportData = createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
        latestDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    //then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(4));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID_2 ), is(40L));
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
    SingleReportDataDto reportData = createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
        latestDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    //then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
  }

  @Test
  public void reportAcrossAllVersions() throws Exception {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    SingleReportDataDto reportData = createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(
        processDefinition.getKey(), ReportConstants.ALL_VERSIONS
    );
    MapSingleReportResultDto result = evaluateReport(reportData);

    //then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(40L));
  }

  @Test
  public void otherProcessDefinitionsDoNotInfluenceResult() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleServiceTaskProcessDefinition();
    ProcessInstanceEngineDto processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 80L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 40L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 20L);
    processInstanceDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), 100L);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData1 = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapSingleReportResultDto result1 = evaluateReport(reportData1);
    SingleReportDataDto reportData2 = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition2);
    MapSingleReportResultDto result2 = evaluateReport(reportData2);

    // then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result1.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(80L));
    Map<String, Long> flowNodeIdToMaximumExecutionDuration2 = result2.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration2.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration2.get(SERVICE_TASK_ID ), is(100L));
  }

  @Test
  public void noEventMatchesReturnsEmptyResult() {

    // when
    SingleReportDataDto reportData =
      createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport("nonExistingProcessDefinitionId", "1");
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(0));
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
    SingleReportDataDto reportData =
      createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(subProcessDefinition.getKey(), String.valueOf(subProcessDefinition.getVersion()));
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(10L));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() throws Exception {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcessDefinition();

    ProcessInstanceEngineDto processInstanceDto;
    for (int i = 0; i < 11; i++) {
      processInstanceDto = engineRule.startProcessInstance(processDefinition.getId());
      engineDatabaseRule.changeActivityDuration(processInstanceDto.getId(), i);
    }
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    Map<String, Long> flowNodeIdToMaximumExecutionDuration = result.getResult();
    assertThat(flowNodeIdToMaximumExecutionDuration.size(), is(3));
    assertThat(flowNodeIdToMaximumExecutionDuration.get(SERVICE_TASK_ID ), is(10L));
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
    SingleReportDataDto reportData = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past.minusSeconds(1L)));
    MapSingleReportResultDto result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(0));

    // when
    reportData = getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition);
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(past, null));
    result = evaluateReport(reportData);

    // then
    assertThat(result.getResult(), is(notNullValue()));
    flowNodeIdToExecutionFrequency = result.getResult();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(SERVICE_TASK_ID ), is(10L));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    SingleReportDataDto dataDto =
      createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    SingleReportDataDto dataDto =
      createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    SingleReportDataDto dataDto =
      createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(PROCESS_DEFINITION_KEY, "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcessDefinition() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess" )
      .startEvent(START_EVENT)
      .serviceTask(SERVICE_TASK_ID)
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private MapSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(MapSingleReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }

  private SingleReportDataDto getMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(ProcessDefinitionEngineDto processDefinition) {
    return createMaxFlowNodeDurationGroupByFlowNodeHeatmapReport(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }

}
