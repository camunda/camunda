package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataReportResultDto;
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
import java.io.IOException;
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

  private RawDataReportResultDto evaluateReportWithFilter(ProcessDefinitionEngineDto processDefinition, List<FilterDto> filter) {
    ReportDataDto reportData =
      createReportDataViewRawAsTable(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    reportData.setFilter(filter);
    return evaluateReport(reportData);
  }

  private RawDataReportResultDto evaluateReport(ReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    MatcherAssert.assertThat(response.getStatus(), is(200));
    return response.readEntity(RawDataReportResultDto.class);
  }

  private Response evaluateReportAndReturnResponse(String processDefinitionKey, ExecutedFlowNodeFilterDto filterDto) {
    ReportDataDto reportData = createReportDataViewRawAsTable(processDefinitionKey, "1");
    List<FilterDto> filter = new ArrayList<>();
    filter.add(filterDto);
    reportData.setFilter(filter);
    return evaluateReportAndReturnResponse(reportData);
  }

  private Response evaluateReportAndReturnResponse(ReportDataDto reportData) {
    return embeddedOptimizeRule.target("report/evaluate")
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(reportData));
  }

  @Test
  public void filterByOneFlowNode() throws Exception {
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

    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);
    // then
    assertResults(result, 1);
  }

  @Test
  public void filterByOneFlowNodeWithUnequalOperator() throws Exception {
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

    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }
  
  @Test
  public void filterMultipleProcessInstancesByOneFlowNodeWithUnequalOperator() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 2);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodesWithUnequalOperator() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

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

  private RawDataReportResultDto getRawDataReportResultDto(ProcessDefinitionEngineDto processDefinition, List<ExecutedFlowNodeFilterDto> executedFlowNodes) {
    ArrayList<FilterDto> filter = new ArrayList<>(executedFlowNodes);
    return evaluateReportWithFilter(processDefinition, filter);
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodes() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodesWithUnequalOperator() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 3);
  }

  @Test
  public void filterByMultipleAndOrCombinedFlowNodes() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result,3);
  }

  @Test
  public void equalAndUnequalOperatorCombined() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

    // then
    assertResults(result, 1);
  }

  private ProcessDefinitionEngineDto deployProcessWIthGatewayAndOneUserTaskEachBranch() throws IOException {
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
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() throws Exception {
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
    RawDataReportResultDto result = getRawDataReportResultDto(processDefinition, executedFlowNodes);

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

  private void assertResults(RawDataReportResultDto resultDto, int piCount) {
    assertThat(resultDto.getResult().size(), is(piCount));
  }


  private ProcessDefinitionEngineDto deployProcessDefinitionWithTwoUserTasks() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
        .startEvent()
          .userTask(USER_TASK_ACTIVITY_ID)
          .userTask(USER_TASK_ACTIVITY_ID_2)
        .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition() throws IOException {
    return deploySimpleUserTaskProcessDefinition(USER_TASK_ACTIVITY_ID);
  }

  private ProcessDefinitionEngineDto deploySimpleUserTaskProcessDefinition(String userTaskActivityId) throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("ASimpleUserTaskProcess" + System.currentTimeMillis())
        .startEvent()
          .userTask(userTaskActivityId)
        .endEvent()
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }


}
