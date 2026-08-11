/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the embedded {@code history[]} batch processing on {@code AGENT_INSTANCE:CREATE}/{@code
 * UPDATE} (#58791). So far: the job-context precondition, checked whenever a jobKey is provided and
 * required once a history batch is present, that keeps the existing PENDING/COMMITTED/DISCARDED
 * lifecycle safe.
 *
 * <p>Per-item shape validation, batch application, and whole-batch atomicity are covered by
 * follow-up commits' own test coverage, not here.
 */
public class AgentInstanceHistoryBatchProcessingTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectHistoryBatchWithoutJobKeyOnCreate() {
    // given — CREATE applies the exact same job-context rule as UPDATE (AgentHistoryBatchHelper
    // is shared, unchanged, between the two processors).
    final var serviceTaskInstance = deployAndCreateProcessInstance();

    // when — no withJobKey(...) call at all
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem("item-1", "hi")))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("jobKey");
  }

  @Test
  public void shouldAllowUpdateWithoutJobAndWithoutHistory() {
    // given — regression test: supplying neither a job nor a history batch must remain valid.
    // validateJobContext's "no jobKey, no history" branch used to be wrongly rejected; this pins
    // that a plain, job-less, history-less UPDATE (the shape every pre-existing status/metrics
    // update in AgentInstanceUpdateTest uses) keeps working.
    final var context = deployCreateAgentInstanceAndActivateJob();

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .update();

    // then
    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
  }

  @Test
  public void shouldRejectWhenJobNotActive() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();

    // when — a jobKey that was never activated
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(999999999L)
            .withHistory(List.of(userItem("item-1", "hi")))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains("999999999");
  }

  @Test
  public void shouldRejectJobKeyNotActiveEvenWithoutHistory() {
    // given — the job-context check runs whenever a jobKey is provided, regardless of whether a
    // history batch is present.
    final var context = deployCreateAgentInstanceAndActivateJob();

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(999999999L)
            .withStatus(AgentInstanceStatus.THINKING)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains("999999999");
  }

  @Test
  public void shouldRejectHistoryBatchWithoutJobKey() {
    // given — once a history batch is present, a job context becomes required: the batch's
    // AGENT_HISTORY items must be attributed to the job that produced them.
    final var context = deployCreateAgentInstanceAndActivateJob();

    // when — no withJobKey(...) call at all
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withHistory(List.of(userItem("item-1", "hi")))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("jobKey");
  }

  @Test
  public void shouldRejectWhenJobLeaseMismatch() {
    // given
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();
    ENGINE.jobs().withType(JOB_TYPE).withLease().activate();

    // when — carries no lease even though the job has one
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(userItem("item-1", "hi")))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(jobKey));
  }

  // --- helpers ---

  private static Context deployCreateAgentInstanceAndActivateJob() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);
    return new Context(agentInstanceKey, elementInstanceKey, jobKey);
  }

  private static Record<ProcessInstanceRecordValue> deployAndCreateProcessInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID, t -> t.zeebeJobType(JOB_TYPE).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId(SERVICE_TASK_ID)
        .getFirst();
  }

  private static long activateJobForProcessInstance(final long processInstanceKey) {
    ENGINE.jobs().withType(JOB_TYPE).activate();
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(JOB_TYPE)
        .getFirst()
        .getKey();
  }

  private static Record<?> createAgentInstance(final long elementInstanceKey) {
    return ENGINE
        .agentInstances()
        .withElementInstanceKey(elementInstanceKey)
        .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
        .create();
  }

  private static AgentHistoryRecord userItem(final String historyItemId, final String text) {
    return new AgentHistoryRecord()
        .setHistoryItemId(historyItemId)
        .setRole(AgentHistoryRole.USER)
        .setLoopIteration(1)
        .addContent(
            new AgentHistoryMessageContent()
                .setContentType(AgentHistoryContentType.TEXT)
                .setText(text));
  }

  private record Context(long agentInstanceKey, long elementInstanceKey, long jobKey) {}
}
