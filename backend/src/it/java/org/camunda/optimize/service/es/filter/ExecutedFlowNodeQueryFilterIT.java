package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.hamcrest.MatcherAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.ReportDataHelper.createReportDataViewRawAsTable;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;


public class ExecutedFlowNodeQueryFilterIT {

  private static final String TEST_DEFINITION = "TestDefinition";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final static String USER_TASK_ACTIVITY_ID = "User-Task";
  private final static String USER_TASK_ACTIVITY_ID_2 = "User-Task2";

  private RawDataSingleReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, List<FilterDto> filter) {
    SingleReportDataDto reportData =
      createReportDataViewRawAsTable(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    reportData.setFilter(filter);
    return evaluateReport(reportData);
  }

  private RawDataSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataSingleReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(String processDefinitionKey, ExecutedFlowNodeFilterDto filterDto) {
    SingleReportDataDto reportData = createReportDataViewRawAsTable(processDefinitionKey, "1");
    List<FilterDto> filter = new ArrayList<>();
    filter.add(filterDto);
    reportData.setFilter(filter);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate/single")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(reportData));
  }

  @Test
  public void filterByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .inOperator()
          .build();

    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);
    // then
    assertResults(result, 1);
  }

  @Test
  public void filterByOneFlowNodeWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .notInOperator()
          .build();

    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .inOperator()
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }
  
  @Test
  public void filterMultipleProcessInstancesByOneFlowNodeWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .notInOperator()
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .and()
          .id(USER_TASK_ACTIVITY_ID_2)
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .inOperator()
          .and()
          .id(USER_TASK_ACTIVITY_ID_2)
          .notInOperator()
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);

    // when
    executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .notInOperator()
          .and()
          .id(USER_TASK_ACTIVITY_ID_2)
          .notInOperator()
          .build();
    result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  private RawDataSingleReportResultDto getRawDataReportResultDto(ProcessDefinitionEngineDto processDefinition, List<ExecutedFlowNodeFilterDto> executedFlowNodes) {
    ArrayList<FilterDto> filter = new ArrayList<>(executedFlowNodes);
    return evaluateReportWithFilter(processDefinition, filter);
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    engineRule.startProcessInstance(processDefinition.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .ids(USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_ID_2)
          .inOperator()
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodesWithUnequalOperator() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .ids("UserTask-PathA", "FinalUserTask")
          .notInOperator()
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void filterByMultipleAndOrCombinedFlowNodes() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes =
      ExecutedFlowNodeFilterBuilder
        .construct()
            .ids("UserTask-PathA", "UserTask-PathB")
            .inOperator()
          .and()
            .id("FinalUserTask")
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result,3);
  }

  @Test
  public void equalAndUnequalOperatorCombined() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinition.getId(), takePathA);
    engineRule.startProcessInstance(processDefinition.getId(), takePathB);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes =
      ExecutedFlowNodeFilterBuilder
        .construct()
            .ids("UserTask-PathA", "FinalUserTask")
            .notInOperator()
          .and()
            .id("UserTask-PathB")
            .inOperator()
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  private ProcessDefinitionEngineDto deployProcessWIthGatewayAndOneUserTaskEachBranch() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
        .startEvent()
        .exclusiveGateway("splittingGateway")
          .condition("Take path A", "${takePathA}")
          .userTask("UserTask-PathA")
          .exclusiveGateway("mergeExclusiveGateway")
          .userTask("FinalUserTask")
          .endEvent()
        .moveToLastGateway()
        .moveToLastGateway()
          .condition("Take path B", "${!takePathA}")
          .userTask("UserTask-PathB")
          .connectTo("mergeExclusiveGateway")
        .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleUserTaskProcessDefinition();
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinition2.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .build();
    RawDataSingleReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void validationExceptionOnNullOperatorField() {
    //given
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = new ExecutedFlowNodeFilterDto();
    ExecutedFlowNodeFilterDataDto flowNodeFilter = new ExecutedFlowNodeFilterDataDto();
    flowNodeFilter.setValues(Collections.singletonList("foo"));
    executedFlowNodeFilter.setData(flowNodeFilter);

    // when
    Response response = evaluateReportAndReturnResponse(TEST_DEFINITION, executedFlowNodeFilter);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullValueField() {
    //given
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = new ExecutedFlowNodeFilterDto();
    ExecutedFlowNodeFilterDataDto flowNodeFilter = new ExecutedFlowNodeFilterDataDto();
    flowNodeFilter.setValues(null);
    executedFlowNodeFilter.setData(flowNodeFilter);


    // when
    Response response = evaluateReportAndReturnResponse(TEST_DEFINITION, executedFlowNodeFilter);

    // then
    assertThat(response.getStatus(),is(500));
  }

  private void assertResults(RawDataSingleReportResultDto resultDto, int piCount) {
    assertThat(resultDto.getResult().size(), is(piCount));
  }


  private ProcessDefinitionEngineDto deployProcessDefinitionWithTwoUserTasks() {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
        .startEvent()
          .userTask(USER_TASK_ACTIVITY_ID)
          .userTask(USER_TASK_ACTIVITY_ID_2)
        .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition() {
    return deploySimpleUserTaskProcessDefinition(USER_TASK_ACTIVITY_ID);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition(String userTaskActivityId) {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("ASimpleUserTaskProcess" + System.currentTimeMillis())
        .startEvent()
          .userTask(userTaskActivityId)
        .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
