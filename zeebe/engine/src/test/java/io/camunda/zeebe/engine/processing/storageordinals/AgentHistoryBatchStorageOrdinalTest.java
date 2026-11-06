/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class AgentHistoryBatchStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 7;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignInstanceOrdinalToCleanUpCommandAndCleanedEvent() {
    // given
    final var jobType = "agent-history-batch-ordinal";
    final var processId = "agent-history-batch-ordinal-process";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "agent-task", t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();
    final long elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst()
            .getKey();
    final var jobBatch = engine.jobs().withType(jobType).withLease().activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .getFirst()
            .getKey();
    final var jobIndex = jobBatch.getValue().getJobKeys().indexOf(jobKey);
    assertThat(jobIndex).isNotEqualTo(-1);
    final var jobLeaseToken = jobBatch.getValue().getJobs().get(jobIndex).getJobLeaseToken();
    final var agentInstanceKey =
        engine
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLeaseToken(jobLeaseToken)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-user")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)))
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    engine.job().withKey(jobKey).withJobLeaseToken(jobLeaseToken).complete();
    RecordingExporter.agentHistoryBatchRecords(AgentHistoryBatchIntent.CLEANED)
        .withAgentInstanceKey(agentInstanceKey)
        .await();

    // then
    assertThat(
            RecordingExporter.agentHistoryBatchRecords(AgentHistoryBatchIntent.CLEAN_UP)
                .withAgentInstanceKey(agentInstanceKey)
                .onlyCommands()
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
    assertThat(
            RecordingExporter.agentHistoryBatchRecords(AgentHistoryBatchIntent.CLEANED)
                .withAgentInstanceKey(agentInstanceKey)
                .getFirst()
                .getValue()
                .getStorageOrdinal())
        .isEqualTo(FIXED_ORDINAL);
  }
}
