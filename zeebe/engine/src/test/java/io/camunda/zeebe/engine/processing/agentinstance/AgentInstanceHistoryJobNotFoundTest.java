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
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRole;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers {@code AGENT_INSTANCE:UPDATE} for a job that is in {@code JobState.State.NOT_FOUND} — i.e.
 * no job record exists for the given {@code jobKey} at all, whether because the job already
 * completed (or was cancelled, or had a caught error) or because the {@code jobKey} never belonged
 * to any job in the first place.
 *
 * <p>Unlike a job that still exists but sits in a non-{@code ACTIVATED} state (handled separately,
 * and left PENDING for later resolution), a {@code NOT_FOUND} job can never resolve: there is no
 * job record left to complete, fail, or have its lease superseded. So the update is accepted, its
 * metrics are accumulated immediately, and its history item is created and discarded in the same
 * processing step — nothing is left pending for a completion that will never arrive.
 *
 * <p><b>Deliberate accepted trade-off:</b> because no job record exists in the {@code NOT_FOUND}
 * case, there is no element instance to validate the update's {@code elementInstanceKey} against.
 * This means a caller-supplied {@code jobKey} that never existed at all is accepted exactly the
 * same way as one whose job genuinely ran to completion. This is an intentional trade-off, not a
 * bug: the caller already passed the coarser authorization check that gates every process instance
 * update, so a fabricated {@code jobKey} lets it do no more than it could already do with a genuine
 * one. Do not "fix" this by resurrecting the old rejection.
 */
public class AgentInstanceHistoryJobNotFoundTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldAcceptAndImmediatelyDiscardUpdateWhenJobHasCompleted() {
    // given — a sequential multi-instance AI-agent service task. A plain single-service-task
    // process won't do here: completing EI1's job also completes EI1, and with a single task that
    // completes the whole process (and the agent instance with it), leaving nothing left to
    // accept an update against. Routing the update through EI2 — still active — isolates the one
    // thing this test is about: job1 itself no longer has a job record at all.
    final var multiInstanceProcessId = "dedup-sequential-multi-instance-update-after-completion";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(multiInstanceProcessId)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t ->
                        t.zeebeJobType(helper.getJobType())
                            .zeebeAiAgentTaskDefinition()
                            .multiInstance(
                                m ->
                                    m.sequential()
                                        .zeebeInputCollectionExpression("items")
                                        .zeebeInputElement("item")))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(multiInstanceProcessId)
            .withVariables(Map.of("items", List.of("a", "b")))
            .create();
    final var ei1 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();

    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();

    ENGINE.jobs().withType(helper.getJobType()).activate();
    ENGINE.job().ofInstance(processInstanceKey).withType(helper.getJobType()).complete();

    final var ei2 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList()
            .get(1)
            .getKey();

    // when — an update reuses job1 (EI1's own, now-completed job) but targets EI2 (still active),
    // so the acceptance reflects job1's own state, not EI2's — which is otherwise valid.
    final var job1Key =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .filter(r -> r.getValue().getElementInstanceKey() == ei1)
            .getFirst()
            .getKey();

    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-after-completion")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("resend after completion"));
    assistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withJobKey(job1Key)
            .withHistory(List.of(assistantItem))
            .update();

    // then
    assertThat(updated.getIntent())
        .as("job1 has no job record left, but the update is accepted anyway")
        .isEqualTo(AgentInstanceIntent.UPDATED);

    // and — its metrics are accumulated even though the job that produced them is long gone.
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(1);

    // and — the history item is created and immediately discarded in the same processing step:
    // nothing will ever complete job1 again, so nothing must be left pending for it.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.CREATED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-after-completion"))
                .exists())
        .as("the item is created before it is discarded")
        .isTrue();
    final var discardedRecord =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.DISCARDED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue())
                        .getHistoryItemId()
                        .equals("item-after-completion"))
            .getFirst();

    // and — the discarded event is trimmed to identity fields only, the same shape every other
    // discard path emits (see
    // AgentHistoryDiscardTest#shouldStripContentToolCallsAndMetricsFromDiscardedEvent).
    final var discarded = (AgentHistoryRecordValue) discardedRecord.getValue();
    assertThat(discarded.getContent()).isEmpty();
    assertThat(discarded.getToolCalls()).isEmpty();
    assertThat(discarded.getMetrics().getInputTokens()).isEqualTo(-1L);

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-after-completion"))
                .exists())
        .as("an item discarded for a job that will never resolve is never committed")
        .isFalse();
  }

  @Test
  public void shouldAcceptAndImmediatelyDiscardUpdateWhenJobKeyNeverExisted() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(helper.getJobType()).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();

    // when — a jobKey that was never activated (nor ever existed as any job at all). Nothing in
    // this state distinguishes it from a job that genuinely completed: both are simply
    // JobState.State.NOT_FOUND. This is the deliberate accepted trade-off documented in the class
    // javadoc — see there for why it is not treated as a bug.
    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-fabricated-job")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    assistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(999999999L)
            .withHistory(List.of(assistantItem))
            .update();

    // then
    assertThat(updated.getIntent())
        .as("the fabricated jobKey never existed, but the update is accepted anyway")
        .isEqualTo(AgentInstanceIntent.UPDATED);

    // and — its metrics are accumulated even though no job ever backed this jobKey.
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(1);

    // and — the history item is created and immediately discarded in the same processing step:
    // nothing will ever complete a job that never existed, so nothing must be left pending for it.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.CREATED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job"))
                .exists())
        .as("the item is created before it is discarded")
        .isTrue();
    final var discardedRecord =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.DISCARDED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue())
                        .getHistoryItemId()
                        .equals("item-fabricated-job"))
            .getFirst();

    // and — the discarded event is trimmed to identity fields only, the same shape every other
    // discard path emits (see
    // AgentHistoryDiscardTest#shouldStripContentToolCallsAndMetricsFromDiscardedEvent).
    final var discarded = (AgentHistoryRecordValue) discardedRecord.getValue();
    assertThat(discarded.getContent()).isEmpty();
    assertThat(discarded.getToolCalls()).isEmpty();
    assertThat(discarded.getMetrics().getInputTokens()).isEqualTo(-1L);

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job"))
                .exists())
        .as("an item discarded for a job that will never resolve is never committed")
        .isFalse();
  }

  @Test
  public void shouldNotReaccumulateMetricsWhenNotFoundUpdateIsResent() {
    // given — a fabricated jobKey that never existed, same fixture as
    // shouldAcceptAndImmediatelyDiscardUpdateWhenJobKeyNeverExisted. Its history item is created
    // and discarded on the spot, so — unlike a superseded lease — nothing is left pending that a
    // resend could match against. Only the per-instance metrics-accumulated ids can prevent this
    // resend from being counted a second time.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(helper.getJobType()).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();

    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-resent-fabricated-job")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    assistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    // when — the first update is accepted, its metrics accumulated, and its history item created
    // and discarded in one step.
    final var updated1 =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(999999999L)
            .withHistory(List.of(assistantItem))
            .update();
    assertThat(updated1.getIntent()).isEqualTo(AgentInstanceIntent.UPDATED);
    assertThat(updated1.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(updated1.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(updated1.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated1.getValue().getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(updated1.getValue().getChangedAttributes())
        .as("the first submission's metrics are a real change")
        .contains("metrics");

    // when — the same historyItemId is resent on a second, separate update against the same
    // fabricated jobKey. Its metrics differ from the first submission's — proving that whatever
    // suppresses the second accumulation matches on the item's id, not on its payload.
    final var resentAssistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-resent-fabricated-job")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi again"));
    resentAssistantItem.getMetrics().setInputTokens(500L).setOutputTokens(900L);
    resentAssistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-2").setToolName("lookup"));

    final var updated2 =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(999999999L)
            .withHistory(List.of(resentAssistantItem))
            .update();

    // then — a resend after a discard is not itself an error: the second update is accepted too.
    assertThat(updated2.getIntent())
        .as("resending after a discard is not an error")
        .isEqualTo(AgentInstanceIntent.UPDATED);

    // and — "metrics" is absent from the second update's changedAttributes: this is the durable
    // signal that its metrics were skipped, not applied a second time.
    assertThat(updated2.getValue().getChangedAttributes())
        .as("the resend's metrics must not be reflected as a change")
        .doesNotContain("metrics");

    // and — the agent instance's cumulative metrics equal a single accumulation, not two.
    assertThat(updated2.getValue().getMetrics().getInputTokens())
        .as("inputTokens must not double-count the resent item")
        .isEqualTo(100L);
    assertThat(updated2.getValue().getMetrics().getOutputTokens())
        .as("outputTokens must not double-count the resent item")
        .isEqualTo(40L);
    assertThat(updated2.getValue().getMetrics().getModelCalls())
        .as("modelCalls must not double-count the resent item")
        .isEqualTo(1);
    assertThat(updated2.getValue().getMetrics().getToolCalls())
        .as("toolCalls must not double-count the resent item")
        .isEqualTo(1);

    // and — the resent item is still created and immediately discarded, just like the first: the
    // dedup is about metrics, not about suppressing the item's own create/discard lifecycle. Both
    // updates share the historyItemId "item-resent-fabricated-job", so under the clock-reset
    // limit each intent must show up exactly twice — once per update — proving the second update
    // produced its own create/discard pair rather than merely reusing the first's.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    final var createdForItem =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.CREATED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue())
                        .getHistoryItemId()
                        .equals("item-resent-fabricated-job"))
            .toList();
    assertThat(createdForItem).as("both the original and the resent item are created").hasSize(2);
    final var discardedForItem =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.DISCARDED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue())
                        .getHistoryItemId()
                        .equals("item-resent-fabricated-job"))
            .toList();
    assertThat(discardedForItem)
        .as("both the original and the resent item are discarded immediately")
        .hasSize(2);
    final var committedForItem =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.COMMITTED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue())
                        .getHistoryItemId()
                        .equals("item-resent-fabricated-job"))
            .toList();
    assertThat(committedForItem)
        .as("an item discarded for a job that will never resolve is never committed")
        .isEmpty();
  }

  @Test
  public void shouldAccumulateMetricsButDropConfigurationWhenJobKeyNeverExisted() {
    // given — a fabricated jobKey that never existed, same fixture as
    // shouldAcceptAndImmediatelyDiscardUpdateWhenJobKeyNeverExisted. The batch carries both an
    // ASSISTANT item (with metrics) and a CONFIGURATION item (with a model/provider change), to
    // show the two are handled independently: metrics accumulate immediately regardless of item
    // role, but a CONFIGURATION item's own changes are only ever applied when its item is
    // committed — and an item discarded for a NOT_FOUND job is never committed.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType(helper.getJobType()).zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();

    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-fabricated-job-assistant")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    assistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-fabricated-job-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
    configItem.setModel("gpt-4o-mini").setProvider("azure-openai");
    configItem.setChangedAttributes(List.of("model", "provider"));

    // when — the fabricated jobKey never existed, so the job is JobState.State.NOT_FOUND. The
    // update is still accepted, exactly as for the ASSISTANT-only case.
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(999999999L)
            .withHistory(List.of(assistantItem, configItem))
            .update();

    // then
    assertThat(updated.getIntent())
        .as("the fabricated jobKey never existed, but the update is accepted anyway")
        .isEqualTo(AgentInstanceIntent.UPDATED);

    // and — the ASSISTANT item's metrics are accumulated even though no job ever backed this
    // jobKey.
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(updated.getValue().getChangedAttributes())
        .as("the metrics accumulation is a real change")
        .contains("metrics");

    // and — the CONFIGURATION item's own model/provider change is never applied: it is discarded
    // together with the ASSISTANT item, so it is never committed, and committing is the only
    // point at which UPDATE ever applies a CONFIGURATION item's changes.
    assertThat(updated.getValue().getDefinition().getModel())
        .as("the definition still reflects what was set at creation")
        .isEqualTo("gpt-4o");
    assertThat(updated.getValue().getDefinition().getProvider())
        .as("the definition still reflects what was set at creation")
        .isEqualTo("openai");
    assertThat(updated.getValue().getChangedAttributes())
        .as("the dropped configuration change must not be reflected as a change")
        .doesNotContain("model", "provider");

    // and — both items are created and immediately discarded in the same processing step, and
    // neither is ever committed: a CONFIGURATION item gets no special treatment in the discard
    // path.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.CREATED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job-assistant"))
                .exists())
        .as("the ASSISTANT item is created before it is discarded")
        .isTrue();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job-assistant"))
                .exists())
        .as(
            "the ASSISTANT item is discarded immediately since the fabricated job will never"
                + " complete")
        .isTrue();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job-assistant"))
                .exists())
        .as("the ASSISTANT item is never committed for a job that will never resolve")
        .isFalse();

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.CREATED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job-config"))
                .exists())
        .as("the CONFIGURATION item is created before it is discarded")
        .isTrue();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job-config"))
                .exists())
        .as(
            "the CONFIGURATION item is discarded immediately, same as any other item on a"
                + " NOT_FOUND job")
        .isTrue();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-fabricated-job-config"))
                .exists())
        .as("the CONFIGURATION item is never committed, so its changes can never be applied")
        .isFalse();
  }
}
