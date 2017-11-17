package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.ExecutedFlowNodeFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class ExecutedFlowNodeQueryFilterIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private final static String USER_TASK_ACTIVITY_ID = "User-Task";
  private final static String USER_TASK_ACTIVITY_ID_2 = "User-Task2";

  @Test
  public void filterByOneFlowNode() throws Exception {
    // given
    String processDefinitionId = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .inOperator()
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertResults(resultMap, USER_TASK_ACTIVITY_ID, 1L);
  }

  @Test
  public void filterByOneFlowNodeWithUnequalOperator() throws Exception {
    // given
    String processDefinitionId = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .notInOperator()
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID), is(nullValue()));
    assertThat(resultMap.getPiCount(), is(1L));
  }

  @Test
  public void filterMultipleProcessInstancesByOneFlowNode() throws Exception {
    // given
    String processDefinitionId = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .inOperator()
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertResults(resultMap, USER_TASK_ACTIVITY_ID, 3L);
  }
  
  @Test
  public void filterMultipleProcessInstancesByOneFlowNodeWithUnequalOperator() throws Exception {
    // given
    String processDefinitionId = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .notInOperator()
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID), is(nullValue()));
    assertThat(resultMap.getPiCount(), is(2L));
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodes() throws Exception {
    // given
    String processDefinitionId = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .and()
          .id(USER_TASK_ACTIVITY_ID_2)
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertResults(resultMap, USER_TASK_ACTIVITY_ID, 2L);
    assertResults(resultMap, USER_TASK_ACTIVITY_ID_2, 2L);
  }

  @Test
  public void filterByMultipleAndCombinedFlowNodesWithUnequalOperator() throws Exception {
    // given
    String processDefinitionId = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    engineRule.startProcessInstance(processDefinitionId);
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
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID), is(1L));
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID_2), is(nullValue()));
    assertThat(resultMap.getPiCount(), is(1L));

    // when
    executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .notInOperator()
          .and()
          .id(USER_TASK_ACTIVITY_ID_2)
          .notInOperator()
          .build();
    queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID), is(nullValue()));
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID_2), is(nullValue()));
    assertThat(resultMap.getPiCount(), is(2L));
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodes() throws Exception {
    // given
    String processDefinitionId = deployProcessDefinitionWithTwoUserTasks();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId);
    engineRule.startProcessInstance(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .ids(USER_TASK_ACTIVITY_ID, USER_TASK_ACTIVITY_ID_2)
          .inOperator()
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID), is(3L));
    assertThat(resultMap.getFlowNodes().get(USER_TASK_ACTIVITY_ID_2), is(2L));
    assertThat(resultMap.getPiCount(), is(3L));
  }

  @Test
  public void filterByMultipleOrCombinedFlowNodesWithUnequalOperator() throws Exception {
    // given
    String processDefinitionId = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.startProcessInstance(processDefinitionId, takePathB);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .ids("UserTask-PathA", "FinalUserTask")
          .notInOperator()
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get("UserTask-PathA"), is(nullValue()));
    assertThat(resultMap.getFlowNodes().get("UserTask-PathB"), is(1L));
    assertThat(resultMap.getFlowNodes().get("FinalUserTask"), is(nullValue()));
    assertThat(resultMap.getPiCount(), is(3L));
  }

  @Test
  public void filterByMultipleAndOrCombinedFlowNodes() throws Exception {
    // given
    String processDefinitionId = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.startProcessInstance(processDefinitionId, takePathB);
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
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get("UserTask-PathA"), is(2L));
    assertThat(resultMap.getFlowNodes().get("UserTask-PathB"), is(1L));
    assertThat(resultMap.getFlowNodes().get("FinalUserTask"), is(3L));
    assertThat(resultMap.getPiCount(), is(3L));
  }

  @Test
  public void equalAndUnequalOperatorCombined() throws Exception {
    // given
    String processDefinitionId = deployProcessWIthGatewayAndOneUserTaskEachBranch();

    Map<String, Object> takePathA = new HashMap<>();
    takePathA.put("takePathA", true);
    Map<String, Object> takePathB = new HashMap<>();
    takePathB.put("takePathA", false);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId, takePathB);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    engineRule.startProcessInstance(processDefinitionId, takePathA);
    engineRule.startProcessInstance(processDefinitionId, takePathB);
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
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertThat(resultMap.getFlowNodes().get("UserTask-PathA"), is(nullValue()));
    assertThat(resultMap.getFlowNodes().get("UserTask-PathB"), is(1L));
    assertThat(resultMap.getFlowNodes().get("FinalUserTask"), is(nullValue()));
    assertThat(resultMap.getPiCount(), is(1L));
  }

  private String deployProcessWIthGatewayAndOneUserTaskEachBranch() throws IOException {
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
    return engineRule.deployProcessAndGetId(modelInstance);
  }

  @Test
  public void sameFlowNodeInDifferentProcessDefinitionDoesNotDistortResult() throws Exception {
    // given
    String processDefinitionId = deploySimpleUserTaskProcessDefinition();
    String processDefinitionId2 = deploySimpleUserTaskProcessDefinition();
    ProcessInstanceEngineDto instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    instanceEngineDto = engineRule.startProcessInstance(processDefinitionId2);
    engineRule.finishAllUserTasks(instanceEngineDto.getId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    List<ExecutedFlowNodeFilterDto> executedFlowNodes = ExecutedFlowNodeFilterBuilder.construct()
          .id(USER_TASK_ACTIVITY_ID)
          .build();
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, executedFlowNodes);
    HeatMapResponseDto resultMap = getHeatMapResponseDto(queryDto);

    // then
    assertResults(resultMap, USER_TASK_ACTIVITY_ID, 2L);
  }

  @Test
  public void validationExceptionOnNullOperatorField() {
    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId("TestDefinition");
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = new ExecutedFlowNodeFilterDto();
    ExecutedFlowNodeFilterDataDto flowNodeFilter = new ExecutedFlowNodeFilterDataDto();
    flowNodeFilter.setValues(Collections.singletonList("foo"));
    executedFlowNodeFilter.setData(flowNodeFilter);

    dto.getFilter().add(executedFlowNodeFilter);

    // when
    Response response = getResponse(dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void validationExceptionOnNullValueField() {
    //given
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId("TestDefinition");
    ExecutedFlowNodeFilterDto executedFlowNodeFilter = new ExecutedFlowNodeFilterDto();
    ExecutedFlowNodeFilterDataDto flowNodeFilter = new ExecutedFlowNodeFilterDataDto();
    flowNodeFilter.setValues(null);
    executedFlowNodeFilter.setData(flowNodeFilter);

    dto.getFilter().add(executedFlowNodeFilter);

    // when
    Response response = getResponse(dto);

    // then
    assertThat(response.getStatus(),is(500));
  }

  private void assertResults(HeatMapResponseDto resultMap, String activityId, long piCount) {
    assertThat(resultMap.getFlowNodes().get(activityId), is(piCount));
    assertThat(resultMap.getPiCount(), is(piCount));
  }

  private HeatMapQueryDto createHeatMapQueryWithFLowNodeFilter(String processDefinitionId,
                                                               List<ExecutedFlowNodeFilterDto> executedFlowNodes) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.getFilter().addAll(executedFlowNodes);
    return dto;
  }

  private String deployProcessDefinitionWithTwoUserTasks() throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
        .startEvent()
          .userTask(USER_TASK_ACTIVITY_ID)
          .userTask(USER_TASK_ACTIVITY_ID_2)
        .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private String deploySimpleUserTaskProcessDefinition() throws IOException {
    return deploySimpleUserTaskProcessDefinition(USER_TASK_ACTIVITY_ID);
  }

  private String deploySimpleUserTaskProcessDefinition(String userTaskActivityId) throws IOException {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("ASimpleUserTaskProcess" + System.currentTimeMillis())
        .startEvent()
          .userTask(userTaskActivityId)
        .endEvent()
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private HeatMapResponseDto getHeatMapResponseDto(HeatMapQueryDto dto) {
    Response response = getResponse(dto);

    // then the status code is okay
    return response.readEntity(HeatMapResponseDto.class);
  }

  private Response getResponse(HeatMapQueryDto dto) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Entity<HeatMapQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/heatmap/frequency")
        .request()
        .header(HttpHeaders.AUTHORIZATION,"Bearer " + token)
        .post(entity);
  }
}
