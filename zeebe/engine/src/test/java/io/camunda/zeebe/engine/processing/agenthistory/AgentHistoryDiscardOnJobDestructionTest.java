/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agenthistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that destroying an agentic job (cancel / caught throw-error) emits {@code
 * AGENT_HISTORY:DISCARD} for the job, so its pending items reach {@code DISCARDED} instead of
 * leaking as {@code PENDING} forever. Non-agentic job destruction must not emit a discard.
 */
public class AgentHistoryDiscardOnJobDestructionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String BOUNDARY_ID = "error-boundary";
  private static final String ERROR_CODE = "boom";
  private static final String AGENTIC_JOB_TYPE = "agentic-task";
  private static final String PLAIN_JOB_TYPE = "plain-service-task";
  private static final String EXTERNAL_AGENT_JOB_TYPE = "external-agent-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDiscardPendingItemsWhenAgenticJobCanceledByProcessInstanceCancellation() {
    // given
    final var fixture =
        deployActivateAndCreatePendingItem(process(AGENTIC_JOB_TYPE), AGENTIC_JOB_TYPE);

    // when
    ENGINE.processInstance().withInstanceKey(fixture.processInstanceKey).cancel();

    // then
    assertItemDiscarded(fixture.jobKey, fixture.itemKey);
  }

  @Test
  public void shouldDiscardPendingItemsWhenAgenticJobCanceledByCancelCommand() {
    // given
    final var fixture =
        deployActivateAndCreatePendingItem(process(AGENTIC_JOB_TYPE), AGENTIC_JOB_TYPE);

    // when — explicit JOB:CANCEL command (JobCancelProcessor path)
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(fixture.jobKey)
            .job(JobIntent.CANCEL, new JobRecord().setType(AGENTIC_JOB_TYPE)));

    // then
    assertItemDiscarded(fixture.jobKey, fixture.itemKey);
  }

  @Test
  public void shouldDiscardPendingItemsWhenAgenticJobThrowsCaughtError() {
    // given
    final var fixture =
        deployActivateAndCreatePendingItem(
            processWithErrorBoundary(AGENTIC_JOB_TYPE), AGENTIC_JOB_TYPE);

    // when — error is caught by the boundary event, so the job is deleted without completing
    ENGINE
        .job()
        .ofInstance(fixture.processInstanceKey)
        .withType(AGENTIC_JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertItemDiscarded(fixture.jobKey, fixture.itemKey);
  }

  @Test
  public void shouldDiscardPendingItemsWhenExternalAgentJobCanceledByCancelCommand() {
    // given — external agent marker, job type does not carry the legacy agentic prefix
    final var fixture =
        deployActivateAndCreatePendingItem(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(EXTERNAL_AGENT_JOB_TYPE).zeebeExternalAgentDefinition())
                .endEvent()
                .done(),
            EXTERNAL_AGENT_JOB_TYPE);

    // when — explicit JOB:CANCEL command (JobCancelProcessor path)
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(fixture.jobKey)
            .job(JobIntent.CANCEL, new JobRecord().setType(EXTERNAL_AGENT_JOB_TYPE)));

    // then
    assertItemDiscarded(fixture.jobKey, fixture.itemKey);
  }

  @Test
  public void shouldDiscardPendingItemsWhenExternalAgentJobThrowsCaughtError() {
    // given — external agent marker, job type does not carry the legacy agentic prefix
    final var fixture =
        deployActivateAndCreatePendingItem(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(EXTERNAL_AGENT_JOB_TYPE).zeebeExternalAgentDefinition())
                .boundaryEvent(BOUNDARY_ID, b -> b.error(ERROR_CODE))
                .endEvent()
                .moveToActivity(SERVICE_TASK_ID)
                .endEvent()
                .done(),
            EXTERNAL_AGENT_JOB_TYPE);

    // when — error is caught by the boundary event, so the job is deleted without completing
    ENGINE
        .job()
        .ofInstance(fixture.processInstanceKey)
        .withType(EXTERNAL_AGENT_JOB_TYPE)
        .withErrorCode(ERROR_CODE)
        .throwError();

    // then
    assertItemDiscarded(fixture.jobKey, fixture.itemKey);
  }

  @Test
  public void shouldDiscardPendingItemsWhenExternalAgentJobCanceledByProcessInstanceCancellation() {
    // given — external agent marker, job type does not carry the legacy agentic prefix
    final var fixture =
        deployActivateAndCreatePendingItem(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(EXTERNAL_AGENT_JOB_TYPE).zeebeExternalAgentDefinition())
                .endEvent()
                .done(),
            EXTERNAL_AGENT_JOB_TYPE);

    // when
    ENGINE.processInstance().withInstanceKey(fixture.processInstanceKey).cancel();

    // then
    assertItemDiscarded(fixture.jobKey, fixture.itemKey);
  }

  @Test
  public void shouldNotDiscardWhenNonAgenticJobIsCanceled() {
    // given — a plain service task with no agent definition anywhere, so it cannot carry a
    // pending history item in the first place
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(PLAIN_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    ENGINE.jobs().withType(PLAIN_JOB_TYPE).activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(PLAIN_JOB_TYPE)
            .getFirst()
            .getKey();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // then — scope by jobKey so residual async records from other tests on the shared engine
    // cannot interfere.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .filter(r -> r.getIntent() == AgentHistoryIntent.DISCARD)
                .filter(r -> ((AgentHistoryRecordValue) r.getValue()).getJobKey() == jobKey)
                .exists())
        .describedAs(
            "no discard command is emitted for a job whose element has no agent definition")
        .isFalse();
  }

  private void assertItemDiscarded(final long jobKey, final long itemKey) {
    // the DISCARD follow-up command is emitted for the destroyed job
    final var discardCommand =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARD)
            .onlyCommands()
            .withJobKey(jobKey)
            .getFirst();
    assertThat(discardCommand.getRecordType()).isEqualTo(RecordType.COMMAND);
    assertThat(discardCommand.getKey()).isEqualTo(-1L);
    assertThat(discardCommand.getValue().getJobLease()).isEmpty();

    // and the pending item is discarded
    final var discarded =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
            .withRecordKey(itemKey)
            .getFirst();
    assertThat(discarded.getKey()).isEqualTo(itemKey);
    assertThat(discarded.getValue().getJobKey()).isEqualTo(jobKey);
  }

  // --- fixture / helpers ---

  private static BpmnModelInstance process(final String jobType) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
        .endEvent()
        .done();
  }

  private static BpmnModelInstance processWithErrorBoundary(final String jobType) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType(jobType).zeebeAiAgentTaskDefinition())
        .boundaryEvent(BOUNDARY_ID, b -> b.error(ERROR_CODE))
        .endEvent()
        .moveToActivity(SERVICE_TASK_ID)
        .endEvent()
        .done();
  }

  private Fixture deployActivateAndCreatePendingItem(
      final BpmnModelInstance model, final String jobType) {
    ENGINE.deployment().withXmlResource(model).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final long elementInstanceKey = serviceTaskInstance.getKey();

    ENGINE.jobs().withType(jobType).activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(jobType)
            .getFirst()
            .getKey();

    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();

    final long itemKey =
        ENGINE
            .agentHistories()
            .withAgentInstanceKey(agentInstanceKey)
            .withJobKey(jobKey)
            .withElementInstanceKey(elementInstanceKey)
            .withRole(AgentHistoryRole.USER)
            .create()
            .getKey();

    return new Fixture(processInstanceKey, elementInstanceKey, jobKey, itemKey);
  }

  private record Fixture(
      long processInstanceKey, long elementInstanceKey, long jobKey, long itemKey) {}
}
