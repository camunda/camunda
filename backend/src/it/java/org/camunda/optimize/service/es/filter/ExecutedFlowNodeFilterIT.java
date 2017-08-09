package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.FilterMapDto;
import org.camunda.optimize.dto.optimize.query.HeatMapQueryDto;
import org.camunda.optimize.dto.optimize.query.HeatMapResponseDto;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class ExecutedFlowNodeFilterIT {

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
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, USER_TASK_ACTIVITY_ID);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, USER_TASK_ACTIVITY_ID, 1L);
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
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, USER_TASK_ACTIVITY_ID);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, USER_TASK_ACTIVITY_ID, 3L);
  }

  @Test
  public void filterByMultipleFlowNodes() throws Exception {
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
    List<String> flowNodeIdsWithFinishedUserTask = new ArrayList<>();
    flowNodeIdsWithFinishedUserTask.add(USER_TASK_ACTIVITY_ID);
    flowNodeIdsWithFinishedUserTask.add(USER_TASK_ACTIVITY_ID_2);
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, flowNodeIdsWithFinishedUserTask);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, USER_TASK_ACTIVITY_ID, 2L);
    assertResults(testDefinition, USER_TASK_ACTIVITY_ID_2, 2L);
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
    HeatMapQueryDto queryDto = createHeatMapQueryWithFLowNodeFilter(processDefinitionId, USER_TASK_ACTIVITY_ID);
    HeatMapResponseDto testDefinition = getHeatMapResponseDto(queryDto);

    // then
    assertResults(testDefinition, USER_TASK_ACTIVITY_ID, 2L);
  }

  private void assertResults(HeatMapResponseDto resultMap, String activityId, long piCount) {
    assertThat(resultMap.getFlowNodes().get(activityId), is(piCount));
    assertThat(resultMap.getPiCount(), is(piCount));
  }

  private HeatMapQueryDto createHeatMapQueryWithFLowNodeFilter(String processDefinitionId, String activityId) {
    return createHeatMapQueryWithFLowNodeFilter(processDefinitionId, Collections.singletonList(activityId));
  }

  private HeatMapQueryDto createHeatMapQueryWithFLowNodeFilter(String processDefinitionId, List<String> activityIds) {
    HeatMapQueryDto dto = new HeatMapQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    FilterMapDto mapDto = new FilterMapDto();
    mapDto.getExecutedFlowNodeIds().addAll(activityIds);
    dto.setFilter(mapDto);
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
