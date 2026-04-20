/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.CREATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeebeUserTaskImportServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;

  @Mock private ProcessInstanceWriter processInstanceWriter;
  @Mock private ProcessDefinitionReader processDefinitionReader;
  @Mock private DatabaseClient databaseClient;

  private ZeebeUserTaskImportService underTest;

  @BeforeEach
  void setUp() {
    when(configurationService.getJobExecutorQueueSize()).thenReturn(10);
    when(configurationService.getJobExecutorThreadCount()).thenReturn(1);
    underTest =
        new ZeebeUserTaskImportService(
            configurationService,
            processInstanceWriter,
            1,
            processDefinitionReader,
            databaseClient);
  }

  @Test
  void shouldNotProduceNegativeIdleDurationWhenSameUserAssignedTwiceConsecutively() {
    // given
    // Two ASSIGNED records for the same user with no UNCLAIM between them. The significant
    // time gap between the second assignment (T=2s) and completion (T=100s) ensures the bug
    // is detectable: without the fix, idle = (1s - 0) + (2s - 100s) = -97s (negative).
    final long processInstanceKey = 100L;
    final long userTaskKey = 200L;
    final long elementInstanceKey = 300L;
    final long startTime = 0L;
    final long firstAssignTime = 1_000L;
    final long secondAssignTime = 2_000L;
    final long completeTime = 100_000L;

    final List<ZeebeUserTaskRecordDto> records =
        List.of(
            createRecord(
                userTaskKey,
                CREATING,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                startTime,
                null),
            createRecord(
                userTaskKey,
                ASSIGNED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                firstAssignTime,
                "user1"),
            createRecord(
                userTaskKey,
                ASSIGNED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                secondAssignTime,
                "user1"),
            createRecord(
                userTaskKey,
                COMPLETED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                completeTime,
                "user1"));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(records);

    // then
    assertThat(result).hasSize(1);
    final List<FlowNodeInstanceDto> flowNodes = result.getFirst().getFlowNodeInstances();
    assertThat(flowNodes).hasSize(1);
    final FlowNodeInstanceDto userTask = flowNodes.getFirst();
    assertThat(userTask.getIdleDurationInMs())
        .as("Idle duration must be non-negative when the same user is assigned twice in a row")
        .isEqualTo(1_000L);
    assertThat(userTask.getWorkDurationInMs())
        .as("Work duration must be non-negative")
        .isGreaterThanOrEqualTo(99_000L);
    assertThat(userTask.getTotalDurationInMs())
        .as("Total duration must be non-negative")
        .isGreaterThanOrEqualTo(100_000L);
  }

  @Test
  void shouldProduceCorrectDurationForUnassignedRecords() {
    // given
    final long processInstanceKey = 100L;
    final long userTaskKey = 200L;
    final long elementInstanceKey = 300L;
    final long startTime = 0L;
    final long firstAssignTime = 1_000L;
    final long unassignTime = 2_000L;
    final long secondAssignTime = 3_000L;
    final long completeTime = 100_000L;

    final List<ZeebeUserTaskRecordDto> records =
        List.of(
            createRecord(
                userTaskKey,
                CREATING,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                startTime,
                null),
            createRecord(
                userTaskKey,
                ASSIGNED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                firstAssignTime,
                "user1"),
            createRecord(
                userTaskKey,
                ASSIGNED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                unassignTime,
                null),
            createRecord(
                userTaskKey,
                ASSIGNED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                secondAssignTime,
                "user1"),
            createRecord(
                userTaskKey,
                COMPLETED,
                processInstanceKey,
                userTaskKey,
                elementInstanceKey,
                completeTime,
                "user1"));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(records);

    // then
    assertThat(result).hasSize(1);
    final List<FlowNodeInstanceDto> flowNodes = result.getFirst().getFlowNodeInstances();
    assertThat(flowNodes).hasSize(1);
    final FlowNodeInstanceDto userTask = flowNodes.getFirst();
    assertThat(userTask.getIdleDurationInMs())
        .as("Idle duration must be non-negative when the same user is assigned twice in a row")
        .isEqualTo(2_000L);
    assertThat(userTask.getWorkDurationInMs())
        .as("Work duration must be non-negative")
        .isGreaterThanOrEqualTo(98_000L);
    assertThat(userTask.getTotalDurationInMs())
        .as("Total duration must be non-negative")
        .isGreaterThanOrEqualTo(100_000L);
  }

  private ZeebeUserTaskRecordDto createRecord(
      final long key,
      final UserTaskIntent intent,
      final long processInstanceKey,
      final long userTaskKey,
      final long elementInstanceKey,
      final long timestamp,
      final String assignee) {
    final ZeebeUserTaskDataDto value = new ZeebeUserTaskDataDto();
    value.setProcessInstanceKey(processInstanceKey);
    value.setUserTaskKey(userTaskKey);
    value.setElementInstanceKey(elementInstanceKey);
    value.setBpmnProcessId("testProcess");
    value.setAssignee(assignee != null ? assignee : "");

    final ZeebeUserTaskRecordDto record = new ZeebeUserTaskRecordDto();
    record.setKey(key);
    record.setTimestamp(timestamp);
    record.setIntent(intent);
    record.setValue(value);
    return record;
  }
}
