package org.camunda.optimize.service.es.report.process.flownode.frequency;

import com.fasterxml.jackson.core.type.TypeReference;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractServiceTaskBuilder;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.ProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.hamcrest.core.Is;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.ProcessReportDataBuilderHelper.createCountFlowNodeFrequencyGroupByFlowNode;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
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
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasMoreNodes() {
    //given
    deployAndStartSimpleServiceTaskProcess();
    ProcessInstanceEngineDto latestProcess = deployProcessWithTwoTasks();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("2"));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      latestProcess.getProcessDefinitionKey(), ALL_VERSIONS
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateReport(reportData);

    //then
    assertThat(result.getResult().getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(4));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(2L));
  }

  @Test
  public void allVersionsRespectLatestNodesOnlyWhereLatestHasLessNodes() {
    //given
    deployProcessWithTwoTasks();
    ProcessInstanceEngineDto latestProcess = deployAndStartSimpleServiceTaskProcess();
    assertThat(latestProcess.getProcessDefinitionVersion(), Is.is("2"));

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      latestProcess.getProcessDefinitionKey(), ALL_VERSIONS
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateReport(reportData);

    //then
    assertThat(result.getResult().getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(2L));
  }

  @Test
  public void reportAcrossAllVersions() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), ALL_VERSIONS
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = result.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(ALL_VERSIONS));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.FLOW_NODE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(result.getResult().getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(2L));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = result.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.FLOW_NODE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(result.getResult().getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(1L));
  }

  @Test
  public void reportEvaluationForOneProcess() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    ProcessReportEvaluationResultDto<ProcessReportMapResultDto> result = evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = result.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey(), is(processInstanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto.getProcessDefinitionVersion(), is(processInstanceDto.getProcessDefinitionVersion()));
    assertThat(resultReportDataDto.getView(), is(notNullValue()));
    assertThat(resultReportDataDto.getView().getEntity(), is(ProcessViewEntity.FLOW_NODE));
    assertThat(resultReportDataDto.getView().getProperty(), is(ProcessViewProperty.FREQUENCY));
    assertThat(result.getResult().getData(), is(notNullValue()));
    assertThat(result.getResult().getProcessInstanceCount(), is(1L));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(1L));
  }

  @Test
  public void runningActivitiesAreConsideredAsWell() {
    // given
    ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getProcessInstanceCount(), is(1L));
    Map<String, Long> mapResult = result.getData();
    assertThat(mapResult.get("startEvent"), is(1L));
    assertThat(mapResult.get("userTask"), is(1L));
  }

  @Test
  public void evaluateReportForMultipleEvents() {
    // given
    ProcessInstanceEngineDto engineDto = deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
    engineRule.startProcessInstance(engineDto.getDefinitionId());
    deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY_2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      engineDto.getProcessDefinitionKey(), engineDto.getProcessDefinitionVersion()
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(2L));
  }

  @Test
  public void evaluateReportForMultipleEventsWithMultipleProcesses() {
    // given
    ProcessInstanceEngineDto instanceDto = deployAndStartSimpleServiceTaskProcess();
    engineRule.startProcessInstance(instanceDto.getDefinitionId());

    ProcessInstanceEngineDto instanceDto2 = deployAndStartSimpleServiceTaskProcess();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData =
      createCountFlowNodeFrequencyGroupByFlowNode(
        instanceDto.getProcessDefinitionKey(),
        instanceDto.getProcessDefinitionVersion()
      );
    final ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse1 = evaluateReport(reportData);
    reportData.setProcessDefinitionKey(instanceDto2.getProcessDefinitionKey());
    reportData.setProcessDefinitionVersion(instanceDto2.getProcessDefinitionVersion());
    final ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluationResponse2 = evaluateReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto1 = evaluationResponse1.getReportDefinition().getData();
    assertThat(resultReportDataDto1.getProcessDefinitionKey(), is(instanceDto.getProcessDefinitionKey()));
    assertThat(resultReportDataDto1.getProcessDefinitionVersion(), is(instanceDto.getProcessDefinitionVersion()));
    assertThat(evaluationResponse1.getResult().getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = evaluationResponse1.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(2L));

    final ProcessReportDataDto resultReportDataDto2 = evaluationResponse2.getReportDefinition().getData();
    assertThat(resultReportDataDto2.getProcessDefinitionKey(), is(instanceDto2.getProcessDefinitionKey()));
    assertThat(resultReportDataDto2.getProcessDefinitionVersion(), is(instanceDto2.getProcessDefinitionVersion()));
    assertThat(evaluationResponse2.getResult().getData(), is(notNullValue()));
    flowNodeIdToExecutionFrequency = evaluationResponse2.getResult().getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(1L));
  }

  @Test
  public void evaluateReportForMoreThenTenEvents() {
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      instanceDto.getProcessDefinitionKey(), instanceDto.getProcessDefinitionVersion()
    );
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(13));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY + 0), is(1L));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployProcessWithTwoTasks();
    deployAndStartSimpleServiceTaskProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(4));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      // expect ascending order
      contains(new ArrayList<>(resultMap.keySet()).stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleUserTaskProcess();
    engineRule.completeUserTaskWithoutClaim(processInstanceDto.getId());
    engineRule.startProcessInstance(processInstanceDto.getDefinitionId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstanceDto.getProcessDefinitionKey(), processInstanceDto.getProcessDefinitionVersion()
    );
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    final List<Long> bucketValues = new ArrayList<>(resultMap.values());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
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
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    List<ProcessDefinitionOptimizeDto> definitions = embeddedOptimizeRule
      .getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, 200);
    assertThat(definitions.size(), is(2));

    //when
    ProcessReportDataDto reportData =
      createCountFlowNodeFrequencyGroupByFlowNode(testMIProcess, "1");
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    //then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(5));
  }

  @Test
  public void dateFilterInReport() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleServiceTaskProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    ProcessReportDataDto reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(past.minusSeconds(1L))
                           .add()
                           .buildList());
    ProcessReportMapResultDto result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    Map<String, Long> flowNodeIdToExecutionFrequency = result.getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(0));

    // when
    reportData = createCountFlowNodeFrequencyGroupByFlowNode(
      processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(past).end(null).add().buildList());
    result = evaluateReport(reportData).getResult();

    // then
    assertThat(result.getData(), is(notNullValue()));
    flowNodeIdToExecutionFrequency = result.getData();
    assertThat(flowNodeIdToExecutionFrequency.size(), is(3));
    assertThat(flowNodeIdToExecutionFrequency.get(TEST_ACTIVITY), is(1L));
  }

  @Test
  public void optimizeExceptionOnViewEntityIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountFlowNodeFrequencyGroupByFlowNode("123", "1");
    dataDto.getView().setEntity(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountFlowNodeFrequencyGroupByFlowNode("123", "1");
    dataDto.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    ProcessReportDataDto dataDto = createCountFlowNodeFrequencyGroupByFlowNode("123", "1");
    dataDto.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess(TEST_ACTIVITY);
  }

  private ProcessInstanceEngineDto deployProcessWithTwoTasks() {
    // @formatter:off

    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent("start")
      .serviceTask(CountFlowNodeFrequencyByFlowNodeReportEvaluationIT.TEST_ACTIVITY)
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
  // @formatter:on

  private ProcessInstanceEngineDto deployAndStartSimpleUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent("startEvent")
      .userTask("userTask")
      .endEvent()
      .done();
    return engineRule.deployAndStartProcess(processModel);
  }


  private ProcessReportEvaluationResultDto<ProcessReportMapResultDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<ProcessReportEvaluationResultDto<ProcessReportMapResultDto>>() {});
      // @formatter:on
  }

  private Response evaluateReportAndReturnResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }


}
