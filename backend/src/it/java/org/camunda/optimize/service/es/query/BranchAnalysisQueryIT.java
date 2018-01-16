package org.camunda.optimize.service.es.query;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.filter.data.VariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class BranchAnalysisQueryIT {
  private Logger logger = LoggerFactory.getLogger(BranchAnalysisQueryIT.class);
  private static final String PROCESS_DEFINITION_ID = "procDef1";
  private static final String GATEWAY_ACTIVITY = "gw_1";

  private static final String GATEWAY_B = "gw_b";
  private static final String GATEWAY_C = "gw_c";
  private static final String GATEWAY_D = "gw_d";
  private static final String GATEWAY_F = "gw_f";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  private static final String START_EVENT_ID = "startEvent";
  private static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  private static final String TASK_ID_1 = "serviceTask1";
  private static final String TASK_ID_2 = "serviceTask2";
  private static final String MERGE_GATEWAY_ID = "mergeExclusiveGateway";
  private static final String END_EVENT_ID = "endEvent";
  private static final String USER_TASK_ID = "userTask";

  private String deploySimpleGatewayProcessDefinition() throws Exception {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask(TASK_ID_1)
        .camundaExpression("${true}")
      .exclusiveGateway(MERGE_GATEWAY_ID)
        .endEvent(END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
        .condition("no", "${!goToTask1}")
        .serviceTask(TASK_ID_2)
        .camundaExpression("${true}")
        .connectTo(MERGE_GATEWAY_ID)
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private String deploySimpleGatewayProcessWithUserTask() throws Exception {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
      .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask(TASK_ID_1)
          .camundaExpression("${true}")
        .exclusiveGateway(MERGE_GATEWAY_ID)
        .endEvent(END_EVENT_ID)
      .moveToNode(SPLITTING_GATEWAY_ID)
        .condition("no", "${!goToTask1}")
        .serviceTask(TASK_ID_2)
          .camundaExpression("${true}")
        .userTask(USER_TASK_ID)
        .connectTo(MERGE_GATEWAY_ID)
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    return processDefinitionId;
  }

  private void startSimpleGatewayProcessAndTakeTask1(String processDefinitionId) throws IOException {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
  }

  private void startSimpleGatewayProcessAndTakeTask2(String processDefinitionId) throws IOException {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
  }

  @Test
  public void branchAnalysis() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinitionId);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  private BranchAnalysisDto performBranchAnalysis(String processDefinitionId) {
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    return getBranchAnalysisDto(dto);
  }

  @Test
  public void branchAnalysisTakingBothPaths() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    startSimpleGatewayProcessAndTakeTask2(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinitionId);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(3L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(2L));
    assertThat(task1.getActivityCount(), is(2L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(1L));
    assertThat(task2.getActivityCount(), is(1L));
  }

  @Test
  public void branchAnalysisNotAllTokensReachedEndEvent() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessWithUserTask();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    startSimpleGatewayProcessAndTakeTask2(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinitionId);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(1L));
  }

  @Test
  public void anotherProcessDefinitionDoesNotAffectAnalysis() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    String processDefinitionId2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask2(processDefinitionId2);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinitionId);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(2L));
    assertThat(task1.getActivityCount(), is(2L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithDtoFilteredByDateBefore() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    OffsetDateTime now = OffsetDateTime.now();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    DateUtilHelper.addDateFilter("<=", "start_date", now, dto);
    logger.debug("Preparing query on [{}] with operator [{}], type [{}], date [{}]", processDefinitionId, "<=", "start_date", now);

    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithDtoFilteredByDateAfter() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    DateUtilHelper.addDateFilter(">", "start_date", OffsetDateTime.now(), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(0L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(0L));
    assertThat(task1.getActivityCount(), is(0L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithGtEndDateCriteria() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    DateUtilHelper.addDateFilter("<", "end_date", nowPlusTimeInMs(1000), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysisWithMixedDateCriteria() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    DateUtilHelper.addDateFilter("<", "end_date", nowPlusTimeInMs(1000), dto);
    DateUtilHelper.addDateFilter(">", "start_date", nowPlusTimeInMs(-2000), dto);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void bypassOfGatewayDoesNotDistortResult() throws Exception {
    //given
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(GATEWAY_B)
        .condition("Take long way", "${!takeShortcut}")
      .exclusiveGateway(GATEWAY_C)
        .condition("Take direct way", "${!goToTask}")
      .exclusiveGateway(GATEWAY_D)
      .exclusiveGateway(GATEWAY_F)
      .endEvent(END_EVENT_ID)
      .moveToNode(GATEWAY_B)
        .condition("Take shortcut", "${takeShortcut}")
        .connectTo(GATEWAY_D)
      .moveToNode(GATEWAY_C)
        .condition("Go to task", "${goToTask}")
        .serviceTask(TASK_ID_1)
          .camundaExpression("${true}")
        .connectTo(GATEWAY_F)
      .done();
    String processDefinitionId = engineRule.deployProcessAndGetId(modelInstance);
    startBypassProcessAndTakeLongWayWithoutTask(processDefinitionId);
    startBypassProcessAndTakeShortcut(processDefinitionId);
    startBypassProcessAndTakeLongWayWithTask(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(GATEWAY_C);
    dto.setEnd(END_EVENT_ID);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto gatewayD = result.getFollowingNodes().get(GATEWAY_D);
    assertThat(gatewayD.getActivityId(), is(GATEWAY_D));
    assertThat(gatewayD.getActivitiesReached(), is(1L));
    assertThat(gatewayD.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task.getActivityId(), is(TASK_ID_1));
    assertThat(task.getActivitiesReached(), is(1L));
    assertThat(task.getActivityCount(), is(1L));
  }

  private void startBypassProcessAndTakeLongWayWithoutTask(String processDefinitionId) throws IOException {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask", false);
    variables.put("takeShortcut", false);
    engineRule.startProcessInstance(processDefinitionId, variables);
  }

  private void startBypassProcessAndTakeShortcut(String processDefinitionId) throws IOException {
    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
  }

  private void startBypassProcessAndTakeLongWayWithTask(String processDefinitionId) throws IOException {
    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", false);
    variables.put("goToTask", true);
    engineRule.startProcessInstance(processDefinitionId, variables);
  }

  @Test
  public void variableFilterWorkInBranchAnalysis() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    dto.setFilter(createVariableFilter());

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(1L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void executedFlowNodeFilterWorksInBranchAnalysis() throws Exception {
    //given
    String processDefinitionId = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinitionId);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(processDefinitionId);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    dto.getFilter().addAll(flowNodeFilter);

    //when
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(0L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(0L));
    assertThat(task1.getActivityCount(), is(0L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  private List<FilterDto> createVariableFilter() {
    VariableFilterDataDto data = new VariableFilterDataDto();
    data.setName("goToTask1");
    data.setType("boolean");
    data.setOperator("=");
    data.setValues(Collections.singletonList("true"));

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void shortcutInExclusiveGatewayDoesNotDistortBranchAnalysis() throws Exception {
    // given
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
    .startEvent("startEvent")
    .exclusiveGateway("splittingGateway")
      .condition("Take long way", "${!takeShortcut}")
      .serviceTask("serviceTask")
        .camundaExpression("${true}")
      .exclusiveGateway("mergeExclusiveGateway")
      .endEvent("endEvent")
    .moveToLastGateway()
    .moveToLastGateway()
      .condition("Take shortcut", "${takeShortcut}")
      .connectTo("mergeExclusiveGateway")
    .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", true);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.deployAndStartProcessWithVariables(modelInstance, variables);
    variables.put("takeShortcut", false);
    engineRule.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(instanceEngineDto.getDefinitionId());
    dto.setGateway("splittingGateway");
    dto.setEnd("endEvent");
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is("endEvent"));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get("serviceTask");
    assertThat(task1.getActivityId(), is("serviceTask"));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get("mergeExclusiveGateway");
    assertThat(task2.getActivityId(), is("mergeExclusiveGateway"));
    assertThat(task2.getActivitiesReached(), is(1L));
    assertThat(task2.getActivityCount(), is(1L));
  }

  @Test
  public void shortcutInMergingFlowNodeDoesNotDistortBranchAnalysis() throws Exception {
    // given
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
    .startEvent("startEvent")
    .exclusiveGateway("splittingGateway")
      .condition("Take long way", "${!takeShortcut}")
      .serviceTask("serviceTask")
        .camundaExpression("${true}")
      .serviceTask("mergingServiceTask")
        .camundaExpression("${true}")
      .endEvent("endEvent")
    .moveToLastGateway()
      .condition("Take shortcut", "${takeShortcut}")
      .connectTo("mergingServiceTask")
    .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", true);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.deployAndStartProcessWithVariables(modelInstance, variables);
    variables.put("takeShortcut", false);
    engineRule.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(instanceEngineDto.getDefinitionId());
    dto.setGateway("splittingGateway");
    dto.setEnd("endEvent");
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is("endEvent"));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get("serviceTask");
    assertThat(task1.getActivityId(), is("serviceTask"));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get("mergingServiceTask");
    assertThat(task2.getActivityId(), is("mergingServiceTask"));
    assertThat(task2.getActivitiesReached(), is(1L));
    assertThat(task2.getActivityCount(), is(1L));
  }

  @Test
  public void endEventDirectlyAfterGateway() throws Exception {
    // given
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
    .startEvent("startEvent")
    .exclusiveGateway("mergeExclusiveGateway")
      .serviceTask()
        .camundaExpression("${true}")
      .exclusiveGateway("splittingGateway")
        .condition("Take another round", "${!anotherRound}")
      .endEvent("endEvent")
    .moveToLastGateway()
      .condition("End process", "${anotherRound}")
      .serviceTask("serviceTask")
        .camundaExpression("${true}")
        .camundaInputParameter("anotherRound", "${anotherRound}")
        .camundaOutputParameter("anotherRound", "${!anotherRound}")
      .connectTo("mergeExclusiveGateway")
    .done();

    Map<String, Object> variables = new HashMap<>();
    variables.put("anotherRound", true);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.deployAndStartProcessWithVariables(modelInstance, variables);
    variables.put("anotherRound", false);
    engineRule.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionId(instanceEngineDto.getDefinitionId());
    dto.setGateway("splittingGateway");
    dto.setEnd("endEvent");
    BranchAnalysisDto result = getBranchAnalysisDto(dto);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is("endEvent"));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get("serviceTask");
    assertThat(task1.getActivityId(), is("serviceTask"));
    assertThat(task1.getActivitiesReached(), is(1L));
    assertThat(task1.getActivityCount(), is(1L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get("endEvent");
    assertThat(task2.getActivityId(), is("endEvent"));
    assertThat(task2.getActivitiesReached(), is(1L));
    assertThat(task2.getActivityCount(), is(1L));
  }

  @Test
  public void testValidationExceptionOnNullDto() {

    //when
    Response response = getResponse(null);
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition() {

    //when
    Response response = getResponse(new BranchAnalysisQueryDto());
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullGateway() {
    //given
    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    //when
    Response response = getResponse(request);

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullEndActivity() {

    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionId(PROCESS_DEFINITION_ID);
    request.setEnd(GATEWAY_ACTIVITY);
    //when
    Response response = getResponse(request);

    assertThat(response.getStatus(), is(500));
  }


  private BranchAnalysisDto getBranchAnalysisDto(BranchAnalysisQueryDto dto) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response = getResponse(token, dto);

    // then the status code is okay
    return response.readEntity(BranchAnalysisDto.class);
  }

  private Response getResponse(String token, BranchAnalysisQueryDto dto) {
    Entity<BranchAnalysisQueryDto> entity = Entity.entity(dto, MediaType.APPLICATION_JSON);
    return embeddedOptimizeRule.target("process-definition/correlation")
      .request()
      .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
      .post(entity);
  }

  private Response getResponse(BranchAnalysisQueryDto request) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    return getResponse(token, request);
  }

  private OffsetDateTime nowPlusTimeInMs(int timeInMs) {
    return OffsetDateTime.now().plus(timeInMs, ChronoUnit.MILLIS);
  }

}