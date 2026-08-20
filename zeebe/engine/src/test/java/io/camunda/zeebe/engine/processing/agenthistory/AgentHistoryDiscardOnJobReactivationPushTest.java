/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordingJobStreamer;
import io.camunda.zeebe.engine.util.RecordingJobStreamer.RecordingJobStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.stream.job.JobActivationPropertiesImpl;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * The job streaming/push path's equivalent of {@link AgentHistoryDiscardOnJobReactivationTest}: a
 * job pushed with a lease can also be re-pushed with a new one (e.g. on {@code fail} with retries),
 * a lease-minting site {@link io.camunda.zeebe.engine.processing.job.JobBatchCollector} never sees.
 */
public class AgentHistoryDiscardOnJobReactivationPushTest {

  private static final String TIMEOUT_MS = "30000";
  private static final RecordingJobStreamer JOB_STREAMER = new RecordingJobStreamer();

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withJobStreamer(JOB_STREAMER);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDiscardPendingItemsWhenPushReactivatesAgenticJobWithNewLease() {
    // given — a leasing stream, an agentic service task, and a CONFIGURATION item pending under
    // the job's first (pushed) lease.
    final var jobType = "reactivation-discard-push";
    final var worker = BufferUtil.wrapString("test");
    final var properties =
        new JobActivationPropertiesImpl()
            .setWorker(worker, 0, worker.capacity())
            .setTimeout(Long.parseLong(TIMEOUT_MS))
            .setTenantIds(List.of(TenantOwned.DEFAULT_TENANT_IDENTIFIER))
            .setWithLease(true);
    final RecordingJobStream jobStream =
        JOB_STREAMER.addJobStream(BufferUtil.wrapString(jobType), properties);

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "agent-task", t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();
    final long elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("agent-task")
            .getFirst()
            .getKey();

    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(1));
    final long jobKey = jobStream.getActivatedJobs().getFirst().jobKey();
    final String firstLease = jobStream.getActivatedJobs().getFirst().jobRecord().getLeaseToken();
    assertThat(firstLease).describedAs("the job under test must actually be leased").isNotEmpty();

    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(firstLease)
            .create()
            .getKey();
    final var configurationItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-1")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(firstLease)
            .withHistory(List.of(configurationItem))
            .update();
    final long pendingItemKey = updated.getValue().getHistory().get(0).getAgentHistoryKey();

    // when — the job fails with retries; the still-open leasing stream re-pushes it with a new,
    // distinct lease token
    ENGINE.job().withKey(jobKey).withLeaseToken(firstLease).withRetries(1).fail();
    await().untilAsserted(() -> assertThat(jobStream.getActivatedJobs()).hasSize(2));
    final String secondLease = jobStream.getActivatedJobs().get(1).jobRecord().getLeaseToken();
    assertThat(secondLease)
        .describedAs("the re-push must mint a new, distinct lease token")
        .isNotEmpty()
        .isNotEqualTo(firstLease);

    // then — a blanket discard (no lease filter) is emitted for the job, and the first lease's
    // pending item is discarded, exactly like the poll path
    final var discardCommand =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARD)
            .onlyCommands()
            .withJobKey(jobKey)
            .getFirst();
    assertThat(discardCommand.getValue().getJobLease()).isEmpty();

    final var discarded =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
            .withRecordKey(pendingItemKey)
            .getFirst();
    assertThat(discarded.getValue().getJobKey()).isEqualTo(jobKey);
  }
}
