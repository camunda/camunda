/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.query;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;

@Slf4j
public class BranchAnalysisQueryIT extends AbstractIT {

  private static final String PROCESS_DEFINITION_KEY = "aProcessDefinitionKey";
  private static final String PROCESS_DEFINITION_VERSION = "1";
  private static final String GATEWAY_ACTIVITY = "gw_1";

  private static final String GATEWAY_B = "gw_b";
  private static final String GATEWAY_C = "gw_c";
  private static final String GATEWAY_D = "gw_d";
  private static final String GATEWAY_F = "gw_f";

  private static final String START_EVENT_ID = "startEvent";
  private static final String SPLITTING_GATEWAY_ID = "splittingGateway";
  private static final String TASK_ID_1 = "serviceTask1";
  private static final String TASK_ID_2 = "serviceTask2";
  private static final String TASK_ID_3 = "serviceTask3";
  private static final String TASK_ID_4 = "serviceTask4";
  private static final String MERGE_GATEWAY_ID = "mergeExclusiveGateway";
  private static final String END_EVENT_ID = "endEvent";
  private static final String USER_TASK_ID = "userTask";

  private static final String SUBPROCESS_TASK_ID_1 = "subprocessTask1";
  private static final String SUBPROCESS_TASK_ID_2 = "subprocessTask2";
  private static final String SUBPROCESS_GATEWAY_ID = "subprocessGateway";
  private static final String SUBPROCESS_END_EVENT_ID = "subprocessEnd";

  @Test
  public void branchAnalysis() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result =
      performBranchAnalysis(processDefinition.getKey(), processDefinition.getVersionAsString());

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_withLoop() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinitionWithLoop();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(
      processDefinition.getKey(),
      processDefinition.getVersionAsString()
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_3);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_3);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_impossiblePath() {
    // given
    ProcessDefinitionEngineDto processDefinition = deployGatewayProcessWithSubprocess();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = analysisClient.performBranchAnalysis(
      processDefinition.getKey(),
      Collections.singletonList(processDefinition.getVersionAsString()),
      Collections.singletonList(null),
      GATEWAY_C,
      SUBPROCESS_END_EVENT_ID
    );

    // then analysis shows it is not possible to reach the chosen end from the chosen gateway
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(SUBPROCESS_END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_3);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_3);
    assertThat(task1.getActivitiesReached()).isEqualTo(0L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_4);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_4);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_acrossAllVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(processDefinition.getKey(), ALL_VERSIONS);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(2L);
    assertThat(task1.getActivityCount()).isEqualTo(2L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_acrossMultipleVersions() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    importAllEngineEntitiesFromScratch();

    // when
    List<String> versions = Arrays.asList(
      processDefinition1.getVersionAsString(),
      processDefinition3.getVersionAsString()
    );
    BranchAnalysisResponseDto result = performBranchAnalysis(
      processDefinition1.getKey(),
      versions
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(4L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(4L);
    assertThat(task1.getActivityCount()).isEqualTo(4L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_latestVersionOnly() {
    // given
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(processDefinition1.getKey(), LATEST_VERSION);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(2L);
    assertThat(task1.getActivityCount()).isEqualTo(2L);

    // when
    ProcessDefinitionEngineDto processDefinition3 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);
    startSimpleGatewayProcessAndTakeTask1(processDefinition3);

    importAllEngineEntitiesFromScratch();

    result = performBranchAnalysis(processDefinition1.getKey(), LATEST_VERSION);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(3L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(3L);
    assertThat(task1.getActivityCount()).isEqualTo(3L);
  }

  @Test
  public void branchAnalysis_noneTenantId() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(
      processDefinition.getKey(),
      processDefinition.getVersionAsString()
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_multipleTenants() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition(tenantId1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition(tenantId2);
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = analysisClient.performBranchAnalysis(
      processDefinition1.getKey(),
      ImmutableList.of(processDefinition1.getVersionAsString()),
      Arrays.asList(tenantId2, tenantId1),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(2L);
    assertThat(task1.getActivityCount()).isEqualTo(2L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysis_specificTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    engineIntegrationExtension.createTenant(tenantId1);
    engineIntegrationExtension.createTenant(tenantId2);
    ProcessDefinitionEngineDto processDefinition1 = deploySimpleGatewayProcessDefinition(tenantId1);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition(tenantId2);
    startSimpleGatewayProcessAndTakeTask1(processDefinition1);
    startSimpleGatewayProcessAndTakeTask1(processDefinition2);

    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = analysisClient.performBranchAnalysis(
      processDefinition1.getKey(),
      ImmutableList.of(processDefinition1.getVersionAsString()),
      Collections.singletonList(tenantId2),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysisTakingBothPaths() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask2(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(
      processDefinition.getKey(),
      processDefinition.getVersion()
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(3L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(2L);
    assertThat(task1.getActivityCount()).isEqualTo(2L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(1L);
    assertThat(task2.getActivityCount()).isEqualTo(1L);
  }

  @Test
  public void branchAnalysisNotAllTokensReachedEndEvent() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessWithUserTask();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask2(processDefinition);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(
      processDefinition.getKey(),
      processDefinition.getVersion()
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(1L);
  }

  @Test
  public void anotherProcessDefinitionDoesNotAffectAnalysis() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    ProcessDefinitionEngineDto processDefinition2 = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask2(processDefinition2);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = performBranchAnalysis(
      processDefinition.getKey(),
      processDefinition.getVersion()
    );

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(2L);
    assertThat(task1.getActivityCount()).isEqualTo(2L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysisWithDtoFilteredByDateBefore() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = startSimpleGatewayProcessAndTakeTask1(processDefinition);
    OffsetDateTime now =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      processDefinition.getKey(),
      Collections.singletonList(String.valueOf(processDefinition.getVersion())),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );

    addStartDateFilter(null, now, dto);
    log.debug(
      "Preparing query on [{}] with operator [{}], type [{}], date [{}]",
      processDefinition,
      "<=",
      "start_date",
      now
    );

    BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(dto);
    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysisWithDtoFilteredByDateAfter() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = startSimpleGatewayProcessAndTakeTask1(processDefinition);
    OffsetDateTime now =
      engineIntegrationExtension.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      processDefinition.getKey(),
      Collections.singletonList(String.valueOf(processDefinition.getVersion())),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
    addStartDateFilter(now.plusSeconds(1L), null, dto);

    // when
    BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(dto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(0L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(0L);
    assertThat(task1.getActivityCount()).isEqualTo(0L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void branchAnalysisWithMixedDateCriteria() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      processDefinition.getKey(),
      Collections.singletonList(String.valueOf(processDefinition.getVersion())),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
    addStartDateFilter(nowPlusTimeInSec(-20), null, dto);

    // when
    BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(dto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void bypassOfGatewayDoesNotDistortResult() {
    // given
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
    ProcessDefinitionEngineDto processDefinition = engineIntegrationExtension.deployProcessAndGetProcessDefinition(
      modelInstance);
    startBypassProcessAndTakeLongWayWithoutTask(processDefinition);
    startBypassProcessAndTakeShortcut(processDefinition);
    startBypassProcessAndTakeLongWayWithTask(processDefinition);
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      processDefinition.getKey(),
      Collections.singletonList(String.valueOf(processDefinition.getVersion())),
      Collections.singletonList(null),
      GATEWAY_C,
      END_EVENT_ID
    );

    // when
    BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(dto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto gatewayD = result.getFollowingNodes().get(GATEWAY_D);
    assertThat(gatewayD.getActivityId()).isEqualTo(GATEWAY_D);
    assertThat(gatewayD.getActivitiesReached()).isEqualTo(1L);
    assertThat(gatewayD.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task.getActivitiesReached()).isEqualTo(1L);
    assertThat(task.getActivityCount()).isEqualTo(1L);
  }

  @Test
  public void variableFilterWorkInBranchAnalysis() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      processDefinition.getKey(),
      Collections.singletonList(processDefinition.getVersionAsString()),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );

    dto.setFilter(ProcessFilterBuilder.filter()
                    .variable()
                    .booleanTrue()
                    .name("goToTask1")
                    .add()
                    .buildList());

    // when
    BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(dto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(1L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
  }

  @Test
  public void executedFlowNodeFilterWorksInBranchAnalysis() {
    // given
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    startSimpleGatewayProcessAndTakeTask1(processDefinition);
    importAllEngineEntitiesFromScratch();

    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      processDefinition.getKey(),
      Collections.singletonList(processDefinition.getVersionAsString()),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );

    List<ProcessFilterDto<?>> flowNodeFilter = ProcessFilterBuilder
      .filter()
      .executedFlowNodes()
      .id("task1")
      .add()
      .buildList();
    dto.getFilter().addAll(flowNodeFilter);

    // when
    BranchAnalysisResponseDto result = analysisClient.getProcessDefinitionCorrelation(dto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo(END_EVENT_ID);
    assertThat(result.getTotal()).isEqualTo(0L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get(TASK_ID_1);
    assertThat(task1.getActivityId()).isEqualTo(TASK_ID_1);
    assertThat(task1.getActivitiesReached()).isEqualTo(0L);
    assertThat(task1.getActivityCount()).isEqualTo(0L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get(TASK_ID_2);
    assertThat(task2.getActivityId()).isEqualTo(TASK_ID_2);
    assertThat(task2.getActivitiesReached()).isEqualTo(0L);
    assertThat(task2.getActivityCount()).isEqualTo(0L);
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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.deployAndStartProcessWithVariables(
      modelInstance,
      variables
    );
    variables.put("takeShortcut", false);
    engineIntegrationExtension.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = getBasicBranchAnalysisDto(instanceEngineDto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo("endEvent");
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get("serviceTask");
    assertThat(task1.getActivityId()).isEqualTo("serviceTask");
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get("mergeExclusiveGateway");
    assertThat(task2.getActivityId()).isEqualTo("mergeExclusiveGateway");
    assertThat(task2.getActivitiesReached()).isEqualTo(1L);
    assertThat(task2.getActivityCount()).isEqualTo(1L);
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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.deployAndStartProcessWithVariables(
      modelInstance,
      variables
    );
    variables.put("takeShortcut", false);
    engineIntegrationExtension.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = getBasicBranchAnalysisDto(instanceEngineDto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo("endEvent");
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get("serviceTask");
    assertThat(task1.getActivityId()).isEqualTo("serviceTask");
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get("mergingServiceTask");
    assertThat(task2.getActivityId()).isEqualTo("mergingServiceTask");
    assertThat(task2.getActivitiesReached()).isEqualTo(1L);
    assertThat(task2.getActivityCount()).isEqualTo(1L);
  }

  private BranchAnalysisResponseDto getBasicBranchAnalysisDto(ProcessInstanceEngineDto instanceEngineDto) {
    BranchAnalysisRequestDto dto = analysisClient.createAnalysisDto(
      instanceEngineDto.getProcessDefinitionKey(),
      Collections.singletonList(String.valueOf(instanceEngineDto.getProcessDefinitionVersion())),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
    return analysisClient.getProcessDefinitionCorrelation(dto);
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
    ProcessInstanceEngineDto instanceEngineDto = engineIntegrationExtension.deployAndStartProcessWithVariables(
      modelInstance,
      variables
    );
    variables.put("anotherRound", false);
    engineIntegrationExtension.startProcessInstance(instanceEngineDto.getDefinitionId(), variables);
    importAllEngineEntitiesFromScratch();

    // when
    BranchAnalysisResponseDto result = getBasicBranchAnalysisDto(instanceEngineDto);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getEndEvent()).isEqualTo("endEvent");
    assertThat(result.getTotal()).isEqualTo(2L);
    assertThat(result.getFollowingNodes()).hasSize(2);

    BranchAnalysisOutcomeDto task1 = result.getFollowingNodes().get("serviceTask");
    assertThat(task1.getActivityId()).isEqualTo("serviceTask");
    assertThat(task1.getActivitiesReached()).isEqualTo(1L);
    assertThat(task1.getActivityCount()).isEqualTo(1L);

    BranchAnalysisOutcomeDto task2 = result.getFollowingNodes().get("endEvent");
    assertThat(task2.getActivityId()).isEqualTo("endEvent");
    assertThat(task2.getActivitiesReached()).isEqualTo(1L);
    assertThat(task2.getActivityCount()).isEqualTo(1L);
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    // when
    Response response = analysisClient.getProcessDefinitionCorrelationRawResponse(null);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinition() {
    // when
    Response response = analysisClient.getProcessDefinitionCorrelationRawResponse(new BranchAnalysisRequestDto());
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void testValidationExceptionOnNullProcessDefinitionVersion() {
    // given
    BranchAnalysisRequestDto request = analysisClient.createAnalysisDto(
      PROCESS_DEFINITION_KEY,
      null,
      null,
      null,
      null
    );

    // when
    Response response = analysisClient.getProcessDefinitionCorrelationRawResponse(request);
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void testValidationExceptionOnNullGateway() {
    // given
    BranchAnalysisRequestDto request = analysisClient.createAnalysisDto(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList(PROCESS_DEFINITION_VERSION),
      null,
      null,
      null
    );

    // when
    Response response = analysisClient.getProcessDefinitionCorrelationRawResponse(request);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void testValidationExceptionOnNullEndActivity() {
    BranchAnalysisRequestDto request = analysisClient.createAnalysisDto(
      PROCESS_DEFINITION_KEY,
      Collections.singletonList(PROCESS_DEFINITION_VERSION),
      Collections.singletonList(null),
      GATEWAY_ACTIVITY,
      null
    );

    // when
    Response response = analysisClient.getProcessDefinitionCorrelationRawResponse(request);

    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private void startBypassProcessAndTakeLongWayWithoutTask(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask", false);
    variables.put("takeShortcut", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }

  private void startBypassProcessAndTakeShortcut(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }

  private void startBypassProcessAndTakeLongWayWithTask(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("takeShortcut", false);
    variables.put("goToTask", true);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }

  private BranchAnalysisResponseDto performBranchAnalysis(final String processDefinitionKey,
                                                          final Integer processDefinitionVersion) {
    return analysisClient.performBranchAnalysis(
      processDefinitionKey,
      ImmutableList.of(processDefinitionVersion.toString()),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
  }

  private BranchAnalysisResponseDto performBranchAnalysis(final String processDefinitionKey,
                                                          final String processDefinitionVersion) {
    return analysisClient.performBranchAnalysis(
      processDefinitionKey,
      ImmutableList.of(processDefinitionVersion),
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
  }

  private BranchAnalysisResponseDto performBranchAnalysis(final String processDefinitionKey,
                                                          final List<String> processDefinitionVersions) {
    return analysisClient.performBranchAnalysis(
      processDefinitionKey,
      processDefinitionVersions,
      Collections.singletonList(null),
      SPLITTING_GATEWAY_ID,
      END_EVENT_ID
    );
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() {
    return deploySimpleGatewayProcessDefinition(null);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition(final String tenantId) {
    BpmnModelInstance modelInstance = BpmnModels.getSimpleGatewayProcess(PROCESS_DEFINITION_KEY);
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, tenantId);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinitionWithLoop() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent()
      .serviceTask(TASK_ID_1)
      .camundaExpression("${true}")
      .exclusiveGateway(SPLITTING_GATEWAY_ID)
        .name("Go to task 2?")
        .condition("yes", "#{goToTask1}")
        .serviceTask(TASK_ID_2)
        .camundaExpression("${true}")
        .endEvent(END_EVENT_ID)
      .moveToLastGateway()
        .condition("no", "#{!goToTask1}")
        .serviceTask(TASK_ID_3)
        .camundaExpression("${true}")
        .connectTo(TASK_ID_1)
        .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance, null);
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
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessDefinitionEngineDto deployGatewayProcessWithSubprocess() {
    // @formatter:off
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(PROCESS_DEFINITION_KEY)
      .startEvent(START_EVENT_ID)
      .exclusiveGateway(GATEWAY_B)
      .name("Should we go to task 1?")
      .condition("no", "${!goToTask1}")
      .serviceTask(TASK_ID_2)
      .camundaExpression("${true}")
      .endEvent("endEvent3")
      .moveToNode(GATEWAY_B)
      .condition("yes", "${goToTask1}")
      .serviceTask(TASK_ID_1)
      .camundaExpression("${true}")
      .subProcess()
        .embeddedSubProcess()
          .startEvent()
          .exclusiveGateway(SUBPROCESS_GATEWAY_ID)
          .condition("yes", "${goToTask1}")
          .serviceTask(SUBPROCESS_TASK_ID_1)
          .camundaExpression("${true}")
          .endEvent(SUBPROCESS_END_EVENT_ID)
          .moveToNode(SUBPROCESS_GATEWAY_ID)
          .condition("no", "${goToTask1}")
          .serviceTask(SUBPROCESS_TASK_ID_2)
          .camundaExpression("${true}")
          .endEvent("subprocessEnd2")
          .subProcessDone()
      .exclusiveGateway(GATEWAY_C)
      .name("Should we go to task 3?")
      .condition("yes", "${goToTask1}")
      .serviceTask(TASK_ID_3)
      .camundaExpression("${true}")
      .endEvent(END_EVENT_ID)
      .moveToNode(GATEWAY_C)
      .condition("no", "${!goToTask1}")
      .serviceTask(TASK_ID_4)
      .camundaExpression("${true}")
      .endEvent("endEvent2")
      .done();
    // @formatter:on

    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private ProcessInstanceEngineDto startSimpleGatewayProcessAndTakeTask1(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    return engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }

  private void startSimpleGatewayProcessAndTakeTask2(ProcessDefinitionEngineDto processDefinition) {
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", false);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId(), variables);
  }

  private void addStartDateFilter(OffsetDateTime startDate, OffsetDateTime endDate, BranchAnalysisRequestDto dto) {
    List<ProcessFilterDto<?>> dateFilter = ProcessFilterBuilder
      .filter()
      .fixedInstanceStartDate()
      .start(startDate)
      .end(endDate)
      .add()
      .buildList();

    dto.getFilter().addAll(dateFilter);
  }

  private OffsetDateTime nowPlusTimeInSec(int timeInMs) {
    return OffsetDateTime.now().plus(timeInMs, ChronoUnit.SECONDS);
  }

}