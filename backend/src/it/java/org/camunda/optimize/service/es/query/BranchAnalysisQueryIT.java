/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

@Slf4j
public class BranchAnalysisQueryIT {
  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String PROCESS_DEFINITION_VERSION = "1";
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

  @Test
  public void branchAnalysis() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result =
      performBranchAnalysis(processDefinition.getKey(), processDefinition.getVersionAsString());

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
  public void branchAnalysis_acrossAllVersions() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinition.getKey(), ALL_VERSIONS);

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
  public void branchAnalysis_acrossMultipleVersions() {
    //given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    ArrayList<String> versions = Lists.newArrayList(
      processDefinition1.getVersionAsString(),
      processDefinition3.getVersionAsString()
    );
    BranchAnalysisDto result = performBranchAnalysis(processDefinition1.getKey(), versions);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(4L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(4L));
    assertThat(task1.getActivityCount(), is(4L));

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId(), is(TASK_ID_2));
    assertThat(task2.getActivitiesReached(), is(0L));
    assertThat(task2.getActivityCount(), is(0L));
  }

  @Test
  public void branchAnalysis_latestVersionOnly() {
    //given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinition1.getKey(), LATEST_VERSION);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(2L));
    assertThat(result.getFollowingNodes().size(), is(2));

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(2L));
    assertThat(task1.getActivityCount(), is(2L));

    // when
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    result = performBranchAnalysis(processDefinition1.getKey(), LATEST_VERSION);

    //then
    assertThat(result, is(notNullValue()));
    assertThat(result.getEndEvent(), is(END_EVENT_ID));
    assertThat(result.getTotal(), is(3L));
    assertThat(result.getFollowingNodes().size(), is(2));

    task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId(), is(TASK_ID_1));
    assertThat(task1.getActivitiesReached(), is(3L));
    assertThat(task1.getActivityCount(), is(3L));
  }

  @Test
  public void branchAnalysis_noneTenantId() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(
      processDefinition.getKey(),
      ImmutableList.of(processDefinition.getVersionAsString()),
      Collections.singletonList(null)
    );

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
  public void branchAnalysis_multipleTenants() {
    //given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition(tenantId1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition(tenantId2);
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(
      processDefinition1.getKey(),
      ImmutableList.of(processDefinition1.getVersionAsString()),
      Lists.newArrayList(tenantId2, tenantId1)
    );

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
  public void branchAnalysis_specificTenant() {
    //given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition(tenantId1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition(tenantId2);
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(
      processDefinition1.getKey(),
      ImmutableList.of(processDefinition1.getVersionAsString()),
      Lists.newArrayList(tenantId2)
    );

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
  public void branchAnalysisTakingBothPaths() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask2(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinition.getKey(), processDefinition.getVersion());

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
  public void branchAnalysisNotAllTokensReachedEndEvent() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessWithUserTask();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask2(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinition.getKey(), processDefinition.getVersion());

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
  public void anotherProcessDefinitionDoesNotAffectAnalysis() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask2(processDefinition2);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisDto result = performBranchAnalysis(processDefinition.getKey(), processDefinition.getVersion());

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
  public void branchAnalysisWithDtoFilteredByDateBefore() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = startSimpleGatewayProcessAndTakeTask1(processDefinition);
    OffsetDateTime now =
      engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinition.getKey());
    dto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);

    addStartDateFilter(null, now, dto);
    log.debug(
      "Preparing query on [{}] with operator [{}], type [{}], date [{}]",
      processDefinition,
      "<=",
      "start_date",
      now
    );

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
  public void branchAnalysisWithDtoFilteredByDateAfter() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = startSimpleGatewayProcessAndTakeTask1(processDefinition);
    OffsetDateTime now =
      engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinition.getKey());
    dto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    addStartDateFilter(now.plusSeconds(1L), null, dto);

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
  public void branchAnalysisWithMixedDateCriteria() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinition.getKey());
    dto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    addStartDateFilter(nowPlusTimeInSec(-20), null, dto);

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
  public void bypassOfGatewayDoesNotDistortResult() {
    //given
    // @formatter:off
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
    // @formatter:on
    ProcessDefinitionEngineDto processDefinition = engineRule.deployProcessAndGetProcessDefinition(modelInstance);
    startBypassProcessAndTakeLongWayWithoutTask(processDefinition);
    startBypassProcessAndTakeShortcut(processDefinition);
    startBypassProcessAndTakeLongWayWithTask(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinition.getKey());
    dto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
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

  @Test
  public void variableFilterWorkInBranchAnalysis() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinition.getKey());
    dto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    dto.setFilter(ProcessFilterBuilder.filter()
                    .variable()
                    .booleanTrue()
                    .name("goToTask1")
                    .add()
                    .buildList());

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
  public void executedFlowNodeFilterWorksInBranchAnalysis() {
    //given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinition.getKey());
    dto.setProcessDefinitionVersion(String.valueOf(processDefinition.getVersion()));
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    List<ProcessFilterDto> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();
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

  @Test
  public void shortcutInExclusiveGatewayDoesNotDistortBranchAnalysis() {
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
    ProcessInstanceEngineDto instanceEngineDto = engineRule.deployAndStartProcessWithVariables(
      modelInstance,
      variables
    );
    variables.put("takeShortcut", false);
    engineRule.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(instanceEngineDto.getProcessDefinitionKey());
    dto.setProcessDefinitionVersion(String.valueOf(instanceEngineDto.getProcessDefinitionVersion()));
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
  public void shortcutInMergingFlowNodeDoesNotDistortBranchAnalysis() {
    // given
    // @formatter:off
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
    // @formatter:on

    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", true);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.deployAndStartProcessWithVariables(
      modelInstance,
      variables
    );
    variables.put("takeShortcut", false);
    engineRule.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(instanceEngineDto.getProcessDefinitionKey());
    dto.setProcessDefinitionVersion(String.valueOf(instanceEngineDto.getProcessDefinitionVersion()));
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
  public void endEventDirectlyAfterGateway() {
    // given
    // @formatter:off
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
    // @formatter:on
    Map<String, Object> variables = new HashMap<>();
    variables.put("anotherRound", true);
    ProcessInstanceEngineDto instanceEngineDto = engineRule.deployAndStartProcessWithVariables(
      modelInstance,
      variables
    );
    variables.put("anotherRound", false);
    engineRule.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    //when
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(instanceEngineDto.getProcessDefinitionKey());
    dto.setProcessDefinitionVersion(String.valueOf(instanceEngineDto.getProcessDefinitionVersion()));
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
  public void testValidationExceptionOnNullProcessDefinitionVersion() {
    //given
    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);

    //when
    Response response = getResponse(new BranchAnalysisQueryDto());
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullGateway() {
    //given
    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    request.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION);
    //when
    Response response = getResponse(request);

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void testValidationExceptionOnNullEndActivity() {

    BranchAnalysisQueryDto request = new BranchAnalysisQueryDto();
    request.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    request.setProcessDefinitionVersion(PROCESS_DEFINITION_VERSION);
    request.setEnd(GATEWAY_ACTIVITY);
    //when
    Response response = getResponse(request);

    assertThat(response.getStatus(), is(500));
  }

  private void startBypassProcessAndTakeLongWayWithoutTask(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask", false);
    variables.put("takeShortcut", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
  }

  private void startBypassProcessAndTakeShortcut(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
  }

  private void startBypassProcessAndTakeLongWayWithTask(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", false);
    variables.put("goToTask", true);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
  }

  private BranchAnalysisDto performBranchAnalysis(final String processDefinitionKey,
                                                  final Integer processDefinitionVersion) {
    return performBranchAnalysis(processDefinitionKey, ImmutableList.of(processDefinitionVersion.toString()));
  }

  private BranchAnalysisDto performBranchAnalysis(final String processDefinitionKey,
                                                  final String processDefinitionVersion) {
    return performBranchAnalysis(processDefinitionKey, ImmutableList.of(processDefinitionVersion));
  }

  private BranchAnalysisDto performBranchAnalysis(final String processDefinitionKey,
                                                  final List<String> processDefinitionVersions) {
    return performBranchAnalysis(processDefinitionKey, processDefinitionVersions, Collections.singletonList(null));
  }

  private BranchAnalysisDto performBranchAnalysis(final String processDefinitionKey,
                                                  final List<String> processDefinitionVersions,
                                                  final List<String> tenantIds) {
    BranchAnalysisQueryDto dto = new BranchAnalysisQueryDto();
    dto.setProcessDefinitionKey(processDefinitionKey);
    dto.setProcessDefinitionVersions(processDefinitionVersions);
    dto.setTenantIds(tenantIds);
    dto.setGateway(SPLITTING_GATEWAY_ID);
    dto.setEnd(END_EVENT_ID);
    return getBranchAnalysisDto(dto);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    return deploySimpleGatewayProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition(final String tenantId) {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
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
    // @formatter:on
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessWithUserTask() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
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
    // @formatter:on
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessInstanceEngineDto startSimpleGatewayProcessAndTakeTask1(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    return engineRule.startProcessInstance(processDefinition.getId(), variables);
  }

  private void startSimpleGatewayProcessAndTakeTask2(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
  }

  private void addStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate, BranchAnalysisQueryDto dto) {
    List<ProcessFilterDto> dateFilter = ProcessFilterBuilder
      .filter()
      .fixedStartDate()
      .start(startDate)
      .end(endDate)
      .add()
      .buildList();

    dto.getFilter().addAll(dateFilter);
  }

  private BranchAnalysisDto getBranchAnalysisDto(BranchAnalysisQueryDto dto) {
    Response response = getRawResponse(dto);

    // then the status code is okay
    return response.readEntity(BranchAnalysisDto.class);
  }

  private Response getRawResponse(BranchAnalysisQueryDto dto) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildProcessDefinitionCorrelation(dto)
      .execute();
  }

  private Response getResponse(BranchAnalysisQueryDto request) {
    return getRawResponse(request);
  }

  private OffsetDateTime nowPlusTimeInSec(int timeInMs) {
    return OffsetDateTime.now().plus(timeInMs, ChronoUnit.SECONDS);
  }

}