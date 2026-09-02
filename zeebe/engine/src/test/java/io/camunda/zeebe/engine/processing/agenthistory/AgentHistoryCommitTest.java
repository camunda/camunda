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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentHistoryCommitTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = "agentic-task";
  private static final String EXTERNAL_SERVICE_TASK_PROCESS_ID = "external-agent-service-task";
  private static final String EXTERNAL_SERVICE_TASK_ID = "external-agent-task";
  private static final String EXTERNAL_SERVICE_TASK_JOB_TYPE = "external-agent-job";
  private static final String EXTERNAL_AD_HOC_PROCESS_ID = "external-agent-ad-hoc-process";
  private static final String EXTERNAL_AD_HOC_SUB_PROCESS_ID = "external-agent-ad-hoc-sub-process";
  private static final String EXTERNAL_AD_HOC_INNER_TASK_ID = "external-agent-ad-hoc-inner-task";
  private static final String EXTERNAL_AD_HOC_JOB_TYPE = "external-agent-ad-hoc-job";
  private static final String EXTERNAL_MULTI_INSTANCE_PROCESS_ID =
      "external-agent-multi-instance-service-task";
  private static final String EXTERNAL_MULTI_INSTANCE_TASK_ID =
      "external-agent-multi-instance-task";
  private static final String EXTERNAL_MULTI_INSTANCE_JOB_TYPE =
      "external-agent-multi-instance-job";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCommitAgentHistoryWhenExternalAgentServiceTaskJobCompletes() {
    // given — external agent marker on a service task, job type does not carry the legacy
    // agentic prefix
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(EXTERNAL_SERVICE_TASK_PROCESS_ID)
                .startEvent()
                .serviceTask(
                    EXTERNAL_SERVICE_TASK_ID,
                    t ->
                        t.zeebeJobType(EXTERNAL_SERVICE_TASK_JOB_TYPE)
                            .zeebeExternalAgentDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(EXTERNAL_SERVICE_TASK_PROCESS_ID).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(EXTERNAL_SERVICE_TASK_ID)
            .getFirst();
    final long elementInstanceKey = serviceTaskInstance.getKey();
    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();

    ENGINE.jobs().withType(EXTERNAL_SERVICE_TASK_JOB_TYPE).activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(EXTERNAL_SERVICE_TASK_JOB_TYPE)
            .getFirst()
            .getKey();
    final long itemKey = createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "");

    // when
    ENGINE.job().withKey(jobKey).complete();

    // then
    final var committed =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withRecordKey(itemKey)
            .getFirst();
    assertThat(committed.getValue().getJobKey())
        .describedAs(
            "a history item for an external agent's job must reach COMMITTED when its job"
                + " completes, even though the job type does not carry the legacy agentic prefix")
        .isEqualTo(jobKey);
  }

  @Test
  public void shouldCommitAgentHistoryWhenExternalAgentAdHocSubProcessJobCompletes() {
    // given — external agent marker on an ad-hoc sub-process, job type does not carry the legacy
    // agentic prefix
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(EXTERNAL_AD_HOC_PROCESS_ID)
                .startEvent()
                .adHocSubProcess(
                    EXTERNAL_AD_HOC_SUB_PROCESS_ID,
                    ahsp -> {
                      ahsp.zeebeExternalAgentDefinition();
                      ahsp.task(EXTERNAL_AD_HOC_INNER_TASK_ID);
                      ahsp.zeebeJobType(EXTERNAL_AD_HOC_JOB_TYPE);
                    })
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(EXTERNAL_AD_HOC_PROCESS_ID).create();
    final var adHocSubProcessInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withElementId(EXTERNAL_AD_HOC_SUB_PROCESS_ID)
            .getFirst();
    final long elementInstanceKey = adHocSubProcessInstance.getKey();
    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();

    ENGINE.jobs().withType(EXTERNAL_AD_HOC_JOB_TYPE).activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(EXTERNAL_AD_HOC_JOB_TYPE)
            .getFirst()
            .getKey();
    final long itemKey = createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "");

    // when
    ENGINE.job().withKey(jobKey).complete();

    // then
    final var committed =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withRecordKey(itemKey)
            .getFirst();
    assertThat(committed.getValue().getJobKey())
        .describedAs(
            "a history item for an external agent's ad-hoc sub-process job must reach COMMITTED"
                + " when its job completes, even though the job type does not carry the legacy"
                + " agentic prefix")
        .isEqualTo(jobKey);
  }

  /**
   * A multi-instance body and its inner activity share the same BPMN element id, but only the inner
   * activity can carry the {@code zeebe:agentDefinition} marker. {@link
   * io.camunda.zeebe.engine.processing.bpmn.behavior.AgentDefinitionBehavior#belongsToAgent}
   * therefore resolves through the multi-instance body to the inner activity before checking for an
   * agent definition.
   *
   * <p>This test proves the runtime half of that resolution — that a completed job on such an
   * element commits its agent history. {@link
   * io.camunda.zeebe.engine.processing.deployment.AgentDefinitionMultiInstanceDeploymentTest}
   * proves the deploy-time half: that the {@code AgentDefinition} is created for the inner
   * activity, not the wrapping body.
   */
  @Test
  public void shouldCommitAgentHistoryWhenExternalAgentMultiInstanceServiceTaskJobCompletes() {
    // given — a service task with an external agent marker that is also configured as a
    // multi-instance activity; the wrapping multi-instance body and the inner service task
    // share the same BPMN element id, EXTERNAL_MULTI_INSTANCE_TASK_ID
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(EXTERNAL_MULTI_INSTANCE_PROCESS_ID)
                .startEvent()
                .serviceTask(
                    EXTERNAL_MULTI_INSTANCE_TASK_ID,
                    t ->
                        t.zeebeJobType(EXTERNAL_MULTI_INSTANCE_JOB_TYPE)
                            .zeebeExternalAgentDefinition()
                            .multiInstance(m -> m.zeebeInputCollectionExpression("[1]")))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(EXTERNAL_MULTI_INSTANCE_PROCESS_ID).create();
    // withElementType(SERVICE_TASK) picks the inner activity's ELEMENT_ACTIVATED record, not the
    // wrapping MULTI_INSTANCE_BODY's — both share EXTERNAL_MULTI_INSTANCE_TASK_ID as element id.
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(EXTERNAL_MULTI_INSTANCE_TASK_ID)
            .getFirst();
    final long elementInstanceKey = serviceTaskInstance.getKey();
    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();

    ENGINE.jobs().withType(EXTERNAL_MULTI_INSTANCE_JOB_TYPE).activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(EXTERNAL_MULTI_INSTANCE_JOB_TYPE)
            .getFirst()
            .getKey();
    final long itemKey = createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "");

    // when
    ENGINE.job().withKey(jobKey).complete();

    // then
    final var committed =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withRecordKey(itemKey)
            .getFirst();
    assertThat(committed.getValue().getJobKey())
        .describedAs(
            "a history item for a multi-instance-wrapped agent's job must reach COMMITTED when"
                + " its job completes")
        .isEqualTo(jobKey);
  }

  @Test
  public void shouldEmitCommittedEventOnCommitCommand() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();

    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);
    final var itemKey = createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "");

    final var committed = ENGINE.agentHistories().withJobKey(jobKey).commit();

    // AgentHistoryCommitProcessor emits COMMITTED carrying only identity/routing fields
    assertThat(committed.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(committed.getKey()).isEqualTo(itemKey);
    assertThat(committed.getValue().getJobKey()).isEqualTo(jobKey);
    assertThat(committed.getValue().getAgentInstanceKey()).isEqualTo(agentInstanceKey);
    assertThat(committed.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);
  }

  @Test
  public void shouldStripContentToolCallsAndMetricsFromCommittedEvent() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    ENGINE
        .agentHistories()
        .withAgentInstanceKey(agentInstanceKey)
        .withJobKey(jobKey)
        .withElementInstanceKey(elementInstanceKey)
        .withRole(AgentHistoryRole.ASSISTANT)
        .withTextContent("some large response text")
        .withToolCall("call-1", "http-tool", "call-activity")
        .withMetrics(100, 50, 1234)
        .create();

    final var committed = ENGINE.agentHistories().withJobKey(jobKey).commit();

    // The item was created with content/toolCalls/metrics, but they are stripped at primary-storage
    // insert — the emitted COMMITTED event must carry none of them.
    assertThat(committed.getValue().getContent()).isEmpty();
    assertThat(committed.getValue().getToolCalls()).isEmpty();
    assertThat(committed.getValue().getMetrics().getInputTokens()).isEqualTo(-1L);
    assertThat(committed.getValue().getMetrics().getOutputTokens()).isEqualTo(-1L);
    assertThat(committed.getValue().getMetrics().getDurationMs()).isEqualTo(-1L);
  }

  @Test
  public void shouldEmitCommittedForAllItemsWithSameJobKey() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // Two items share jobKey but have different leases — COMMIT with no lease must commit both
    // regardless of lease.
    final long firstItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-a");
    final long secondItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-b");

    // An item on an unrelated job must not be committed.
    createUnrelatedJobHistoryItem("lease-a");

    final var firstCommitted = ENGINE.agentHistories().withJobKey(jobKey).commit();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // COMMIT with no lease → visitByJobKey commits all items for jobKey regardless of lease.
    // withSourceRecordPosition scopes to events from this COMMIT command only; the unrelated job's
    // item is not visited by visitByJobKey(jobKey) so no event for it can appear.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .containsExactlyInAnyOrder(firstItemKey, secondItemKey);
  }

  @Test
  public void shouldCommitMatchingLeaseAndDiscardSupersededOnLeaseBasedCommit() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);

    // lease-1 and lease-2 items share jobKey; the unrelated job's item also carries lease-1 — it
    // must not be affected by the COMMIT, proving the filter is scoped to jobKey.
    final long lease1ItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-1");
    final long lease2ItemKey =
        createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, "lease-2");
    createUnrelatedJobHistoryItem("lease-1");

    final var firstCommitted =
        ENGINE.agentHistories().withJobKey(jobKey).withJobLease("lease-1").commit();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // visitByJobLease(lease-1) → lease-1 item COMMITTED
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .containsExactly(lease1ItemKey);

    // discard pass (visitByJobKey) → lease-2 item DISCARDED as superseded activation
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .containsExactly(lease2ItemKey);
  }

  @Test
  public void shouldCommitWinningAndDiscardSupersededOnLeasedJobCompletion() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();

    // Activation 1 (superseded): create a history item, then fail to trigger re-activation.
    final var job1 = activateJobForProcessInstanceWithLease(processInstanceKey);
    final long supersededItemKey =
        createHistoryItem(agentInstanceKey, job1.key(), elementInstanceKey, job1.leaseToken());

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withLeaseToken(job1.leaseToken())
        .withRetries(1)
        .fail();

    // Activation 2 (winning): create a history item, then complete the job.
    final var job2 = activateJobForProcessInstanceWithLease(processInstanceKey);
    assertThat(job2.key()).as("re-activation must reuse the same job key").isEqualTo(job1.key());
    assertThat(job2.leaseToken())
        .as("re-activation must advance the lease token")
        .isNotEqualTo(job1.leaseToken());
    final long winningItemKey =
        createHistoryItem(agentInstanceKey, job2.key(), elementInstanceKey, job2.leaseToken());

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withLeaseToken(job2.leaseToken())
        .complete();

    // JobCompleteProcessor emits AGENT_HISTORY:COMMIT; scope subsequent assertions to it.
    final var firstCommitted =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withJobKey(job2.key())
            .getFirst();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // visitByJobLease(job2.leaseToken()) → winning item COMMITTED
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .as("only the winning activation's history item should be committed")
        .containsExactly(winningItemKey);

    // discard pass → superseded item DISCARDED
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .as("the superseded activation's history item should be discarded")
        .containsExactly(supersededItemKey);
  }

  @Test
  public void shouldKeepBothLeasesMetricsAfterDiscardingSupersededItemOnJobCompletion() {
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();

    // Activation 1 (superseded): an AGENT_INSTANCE:UPDATE accumulates its item's metrics onto the
    // agent instance immediately, before the job later fails and is re-activated.
    final var job1 = activateJobForProcessInstanceWithLease(processInstanceKey);
    final var supersededItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-superseded")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("first attempt"));
    supersededItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(job1.key())
            .withJobLease(job1.leaseToken())
            .withHistory(List.of(supersededItem))
            .update();
    final long supersededItemKey = firstUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withLeaseToken(job1.leaseToken())
        .withRetries(1)
        .fail();

    // Activation 2 (winning): a second item's metrics are accumulated on top of the first's,
    // then the job completes.
    final var job2 = activateJobForProcessInstanceWithLease(processInstanceKey);
    final var winningItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-winning")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("second attempt"));
    winningItem.getMetrics().setInputTokens(50L).setOutputTokens(20L);
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(job2.key())
            .withJobLease(job2.leaseToken())
            .withHistory(List.of(winningItem))
            .update();
    final long winningItemKey = secondUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    // both items' deltas are already summed on the agent instance, before either item is
    // committed or discarded: metrics apply at accept time, not at commit time.
    assertThat(secondUpdate.getValue().getMetrics().getInputTokens())
        .as("both activations' input tokens are summed on the agent instance")
        .isEqualTo(100L + 50L);
    assertThat(secondUpdate.getValue().getMetrics().getOutputTokens())
        .as("both activations' output tokens are summed on the agent instance")
        .isEqualTo(40L + 20L);
    assertThat(secondUpdate.getValue().getMetrics().getModelCalls())
        .as("both activations' model calls are counted on the agent instance")
        .isEqualTo(2);

    // when — the job completes under the winning lease
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withLeaseToken(job2.leaseToken())
        .complete();

    // JobCompleteProcessor emits AGENT_HISTORY:COMMIT; scope subsequent assertions to it.
    final var firstCommitted =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withJobKey(job2.key())
            .getFirst();
    final long commitPosition = firstCommitted.getSourceRecordPosition();
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(r -> r.getSourceRecordPosition() == commitPosition)
                .map(Record::getKey))
        .as("only the winning activation's history item should be committed")
        .containsExactly(winningItemKey);
    final var discarded =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.DISCARDED)
            .filter(r -> r.getSourceRecordPosition() == commitPosition)
            .getFirst();
    assertThat(discarded.getKey())
        .as("the superseded activation's history item should be discarded")
        .isEqualTo(supersededItemKey);

    // and — the agent instance completes right after (its only service task's job just
    // completed), carrying the metrics it held at that point. The discard is confirmed to have
    // already applied by then, so this is not a snapshot taken before the discard: if discarding
    // the superseded item had subtracted the metrics it already contributed at accept time, this
    // total would be missing them.
    final var agentInstanceCompleted =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
            .withAgentInstanceKey(agentInstanceKey)
            .getFirst();
    assertThat(discarded.getPosition())
        .as("the discard must already be applied when the agent instance completes")
        .isLessThan(agentInstanceCompleted.getPosition());
    assertThat(agentInstanceCompleted.getValue().getMetrics().getInputTokens())
        .as("the discarded item's input tokens are still included after the job completed")
        .isEqualTo(100L + 50L);
    assertThat(agentInstanceCompleted.getValue().getMetrics().getOutputTokens())
        .as("the discarded item's output tokens are still included after the job completed")
        .isEqualTo(40L + 20L);
    assertThat(agentInstanceCompleted.getValue().getMetrics().getModelCalls())
        .as("the discarded item's model call is still counted after the job completed")
        .isEqualTo(2);
  }

  // --- helpers ---

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

  private static long createHistoryItem(
      final long agentInstanceKey,
      final long jobKey,
      final long elementInstanceKey,
      final String jobLease) {
    return ENGINE
        .agentHistories()
        .withAgentInstanceKey(agentInstanceKey)
        .withJobKey(jobKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobLease(jobLease)
        .withRole(AgentHistoryRole.USER)
        .create()
        .getKey();
  }

  /**
   * Creates a second, unrelated agentic job (a new process instance of the already-deployed
   * process) with its own agent instance, and a single history item on it with the given lease.
   * Used as a control case to prove an operation scoped to one job does not affect another.
   */
  private static long createUnrelatedJobHistoryItem(final String jobLease) {
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final long elementInstanceKey = serviceTaskInstance.getKey();

    final long agentInstanceKey = createAgentInstance(elementInstanceKey).getKey();
    final long jobKey = activateJobForProcessInstance(processInstanceKey);

    return createHistoryItem(agentInstanceKey, jobKey, elementInstanceKey, jobLease);
  }

  private static ActivatedJob activateJobForProcessInstanceWithLease(
      final long processInstanceKey) {
    final var batchRecord = ENGINE.jobs().withType(JOB_TYPE).withLease().activate();
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(JOB_TYPE)
            .getFirst()
            .getKey();
    final int jobIndex = batchRecord.getValue().getJobKeys().indexOf(jobKey);
    assertThat(jobIndex)
        .as("expected activated job batch to contain job key %d", jobKey)
        .isGreaterThanOrEqualTo(0);
    final String leaseToken = batchRecord.getValue().getJobs().get(jobIndex).getLeaseToken();
    return new ActivatedJob(jobKey, leaseToken);
  }

  private record ActivatedJob(long key, String leaseToken) {}
}
