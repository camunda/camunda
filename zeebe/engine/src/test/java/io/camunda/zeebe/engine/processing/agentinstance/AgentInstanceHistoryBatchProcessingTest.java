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
import io.camunda.zeebe.engine.util.client.AgentInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentHistoryRecordValue;
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
 * Covers job-context validation, batch validation, and batch application for the embedded {@code
 * history[]} batch on {@code AGENT_INSTANCE:CREATE}/{@code UPDATE}.
 */
public class AgentInstanceHistoryBatchProcessingTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "agent-task";
  private static final String JOB_TYPE = JobRecord.IO_CAMUNDA_AI_AGENT_JOB_WORKER_TYPE_PREFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectHistoryBatchWithoutJobKeyOnCreate() {
    // given — CREATE applies the exact same job-context rule as UPDATE (AgentHistoryBatchBehavior
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
  public void shouldRejectWholeBatchWhenAnItemIsMissingHistoryItemId() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var validItem = userItem("item-user", "hi");
    final var invalidItem = new AgentHistoryRecord().setRole(AgentHistoryRole.USER);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(validItem, invalidItem))
            .expectRejection()
            .update();

    // then — the whole batch is rejected, referencing the offending item's index; nothing created
    // (the command was rejected before any AGENT_HISTORY:CREATED event could be appended).
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("index 1", "historyItemId is missing");
  }

  @Test
  public void shouldRejectWholeBatchWhenRoleUnspecified() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var invalidItem = new AgentHistoryRecord().setHistoryItemId("item-1");

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(invalidItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("item-1", "UNSPECIFIED");
  }

  @Test
  public void shouldRejectWholeBatchWhenLoopIterationMissing() {
    // given — the 0 default (loopIteration left unset) is rejected the same as an explicit 0 or a
    // negative value: none of those are valid loopIteration numbers.
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var invalidItem =
        new AgentHistoryRecord().setHistoryItemId("item-1").setRole(AgentHistoryRole.USER);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(invalidItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("item-1", "loopIteration is missing");
  }

  @Test
  public void shouldRejectWholeBatchWhenConfigurationItemHasUnknownChangedAttribute() {
    // given — a CONFIGURATION item naming an attribute this helper doesn't know how to apply (as
    // opposed to a request-level unknown attribute, which is a different check entirely).
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var invalidItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setChangedAttributes(List.of("model", "elementInstanceKey"));

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(invalidItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("item-config", "elementInstanceKey");
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

  @Test
  public void shouldEmitHistoryEventForEachItemInOrderOnUpdate() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var userItem = userItem("item-user", "Please summarize this document.");
    final var assistantItem =
        assistantItem("item-assistant", "Sure, here is the summary.", 100L, 40L);
    final var toolResultItem = toolResultItem("item-tool-result", "lookup result");

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(userItem, assistantItem, toolResultItem))
            .update();

    // then — three AGENT_HISTORY:CREATED events, one per item, in array order.
    final var historyEvents =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(context.agentInstanceKey())
            .limit(3)
            .toList();
    assertThat(historyEvents).hasSize(3);
    assertThat(historyEvents)
        .extracting(e -> e.getValue().getHistoryItemId())
        .containsExactly("item-user", "item-assistant", "item-tool-result");
    assertThat(historyEvents)
        .extracting(e -> e.getValue().getRole())
        .containsExactly(
            AgentHistoryRole.USER, AgentHistoryRole.ASSISTANT, AgentHistoryRole.TOOL_RESULT);
    assertThat(historyEvents)
        .allSatisfy(
            e -> {
              assertThat(e.getValue().getElementInstanceKey())
                  .isEqualTo(context.elementInstanceKey());
              assertThat(e.getValue().getJobKey()).isEqualTo(context.jobKey());
            });

    // the response echoes each built AGENT_HISTORY item back on the UPDATED event, in order.
    final var echoedHistory = updated.getValue().getHistory();
    assertThat(echoedHistory).hasSize(3);
    assertThat(echoedHistory)
        .extracting(AgentHistoryRecordValue::getAgentHistoryKey)
        .containsExactly(
            historyEvents.get(0).getValue().getAgentHistoryKey(),
            historyEvents.get(1).getValue().getAgentHistoryKey(),
            historyEvents.get(2).getValue().getAgentHistoryKey());
  }

  @Test
  public void shouldAccumulateMetricsImmediatelyOnUpdate() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var assistantItem =
        assistantItem("item-assistant", "Sure, here is the summary.", 100L, 40L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(assistantItem))
            .update();

    // then — the supplied token metrics are applied, and modelCalls/toolCalls are derived from the
    // ASSISTANT item itself: one model call, plus one tool call for the one it dispatched.
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(updated.getValue().getChangedAttributes()).contains("metrics");
  }

  @Test
  public void shouldDeriveModelCallsAndToolCallsFromAssistantItemWithoutExplicitMetrics() {
    // given — an ASSISTANT item carrying no token metrics at all still represents one model call,
    // plus one tool call per entry in its own toolCalls list.
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var assistantItem =
        assistantItem("item-assistant", "Sure, here is the summary.", -1L, -1L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-2").setToolName("search"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(assistantItem))
            .update();

    // then
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(2);
    assertThat(updated.getValue().getChangedAttributes()).contains("metrics");
  }

  @Test
  public void shouldAccumulateMetricsOnAnyRole() {
    // given — metrics are not restricted to ASSISTANT items; a TOOL_RESULT item carrying them is
    // accumulated exactly like an ASSISTANT one.
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var toolResultItem = toolResultItem("item-tool-result", "lookup result");
    toolResultItem.getMetrics().setInputTokens(5L).setOutputTokens(2L);

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(toolResultItem))
            .update();

    // then
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(5L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(2L);
    assertThat(updated.getValue().getChangedAttributes()).contains("metrics");
  }

  @Test
  public void shouldNotAccumulateMetricsWhenItemCarriesNone() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var userItem = userItem("item-user", "hello");

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(userItem))
            .update();

    // then
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(0);
    assertThat(updated.getValue().getChangedAttributes()).doesNotContain("metrics");
  }

  @Test
  public void shouldApplyInstanceFieldsFromConfigurationItemOnUpdate() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var configItem = configItem("item-config");
    configItem.setModel("gpt-4o-mini").setProvider("openai");
    configItem.addSystemPrompt(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("You are a helpful agent."));
    configItem.setTools(List.of(new AgentInstanceTool().setName("calc").setElementId("calc-task")));
    configItem.getLimits().setMaxTokens(5000L).setMaxModelCalls(8).setMaxToolCalls(16);
    configItem.setChangedAttributes(
        List.of(
            "model",
            "provider",
            "systemPrompt",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(configItem))
            .update();

    // then — applied immediately onto the live fields, driven by the item's own changedAttributes.
    assertThat(updated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(updated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(updated.getValue().getDefinition().getSystemPrompt())
        .isEqualTo("You are a helpful agent.");
    assertThat(updated.getValue().getTools()).extracting("name").containsExactly("calc");
    assertThat(updated.getValue().getLimits().getMaxTokens()).isEqualTo(5000L);
    assertThat(updated.getValue().getLimits().getMaxModelCalls()).isEqualTo(8);
    assertThat(updated.getValue().getLimits().getMaxToolCalls()).isEqualTo(16);
    assertThat(updated.getValue().getChangedAttributes())
        .containsExactlyInAnyOrder(
            "systemPrompt",
            "model",
            "provider",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls");

    // the persisted AGENT_HISTORY event is a full copy of the item, including these fields.
    final var historyEvent =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(context.agentInstanceKey())
            .getFirst();
    assertThat(historyEvent.getValue().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(historyEvent.getValue().getProvider()).isEqualTo("openai");
  }

  @Test
  public void shouldOnlyApplyAttributesNamedInConfigurationItemChangedAttributes() {
    // given — deployCreateAgentInstanceAndActivateJob() creates the instance with
    // model="gpt-4o"/provider="openai" already set. The item carries a different value for both,
    // but only names "model" in its own changedAttributes: provider must be left at its original
    // value, since presence alone no longer drives application (that's what tells apart "left
    // as-is" from "deliberately cleared").
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var configItem = configItem("item-config");
    configItem.setModel("gpt-4o-mini").setProvider("anthropic");
    configItem.setChangedAttributes(List.of("model"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(configItem))
            .update();

    // then
    assertThat(updated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(updated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(updated.getValue().getChangedAttributes()).containsExactly("model");
  }

  @Test
  public void shouldNotApplyInstanceFieldsFromNonConfigurationItem() {
    // given — a USER item carrying model/provider data (e.g. echoed by a misbehaving client) must
    // not affect AgentInstance state: only CONFIGURATION items ever do. The instance was created
    // with model="gpt-4o"/provider="openai" (see deployCreateAgentInstanceAndActivateJob()); the
    // item carries different values to prove they never land.
    final var context = deployCreateAgentInstanceAndActivateJob();
    final var userItem = userItem("item-user", "hello");
    userItem.setModel("gpt-4o-mini").setProvider("anthropic");
    userItem.setChangedAttributes(List.of("model", "provider"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(userItem))
            .update();

    // then
    assertThat(updated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(updated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(updated.getValue().getChangedAttributes()).doesNotContain("model", "provider");
  }

  @Test
  public void shouldRejectDirectMetricsChangeWhenHistoryIsPresent() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();

    // when — old-style direct metrics delta combined with a history batch in the same request:
    // "metrics" drops out of the allowed set once history is present, so this is rejected exactly
    // like any other unrecognized attribute would be.
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(userItem("item-1", "hi")))
            .withMetricsDelta(10L, 5L, 1, 0)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("metrics");
  }

  @Test
  public void shouldRejectDirectToolsChangeWhenHistoryIsPresent() {
    // given
    final var context = deployCreateAgentInstanceAndActivateJob();

    // when — old-style direct tools change combined with a history batch in the same request
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withJobKey(context.jobKey())
            .withHistory(List.of(userItem("item-1", "hi")))
            .withTools(List.of(AgentInstanceClient.tool("calc", "a calculator", "calc-task")))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("tools");
  }

  @Test
  public void shouldEmitHistoryEventsOnCreate() {
    // given
    final var serviceTaskInstance = deployAndCreateProcessInstance();
    final var elementInstanceKey = serviceTaskInstance.getKey();
    final var processInstanceKey = serviceTaskInstance.getValue().getProcessInstanceKey();
    final var jobKey = activateJobForProcessInstance(processInstanceKey);
    final var userItem = userItem("item-user", "hi");

    // when
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(userItem))
            .create();

    // then
    final var historyEvent =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(created.getKey())
            .getFirst();
    assertThat(historyEvent.getValue().getHistoryItemId()).isEqualTo("item-user");
    assertThat(historyEvent.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);
    // changedAttributes stays empty on CREATED, unaffected by the history batch.
    assertThat(created.getValue().getChangedAttributes()).isEmpty();
  }

  @Test
  public void shouldResetEchoedHistoryOnSubsequentUpdateWithoutABatch() {
    // given — first update carries a batch
    final var context = deployCreateAgentInstanceAndActivateJob();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(context.agentInstanceKey())
        .withElementInstanceKey(context.elementInstanceKey())
        .withJobKey(context.jobKey())
        .withHistory(List.of(userItem("item-1", "hi")))
        .update();

    // when — a second, status-only update carries no batch at all
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(context.agentInstanceKey())
            .withElementInstanceKey(context.elementInstanceKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .update();

    // then — the previous batch must not leak forward onto an unrelated update. This also proves
    // history never round-trips through primary storage: `current` here was loaded fresh via
    // agentInstanceState.getRecord(), and AgentInstanceCreatedApplier/UpdatedApplier strip history
    // before ever storing a record — if they didn't, the first update's batch would still be
    // sitting in state and would leak back out here.
    assertThat(secondUpdate.getValue().getHistory()).isEmpty();
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

  private static AgentHistoryRecord assistantItem(
      final String historyItemId,
      final String text,
      final long inputTokens,
      final long outputTokens) {
    final var item =
        new AgentHistoryRecord()
            .setHistoryItemId(historyItemId)
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText(text));
    item.getMetrics().setInputTokens(inputTokens).setOutputTokens(outputTokens);
    return item;
  }

  private static AgentHistoryRecord toolResultItem(final String historyItemId, final String text) {
    return new AgentHistoryRecord()
        .setHistoryItemId(historyItemId)
        .setRole(AgentHistoryRole.TOOL_RESULT)
        .setLoopIteration(1)
        .addContent(
            new AgentHistoryMessageContent()
                .setContentType(AgentHistoryContentType.TEXT)
                .setText(text));
  }

  private static AgentHistoryRecord configItem(final String historyItemId) {
    return new AgentHistoryRecord()
        .setHistoryItemId(historyItemId)
        .setRole(AgentHistoryRole.CONFIGURATION)
        .setLoopIteration(1);
  }

  private record Context(long agentInstanceKey, long elementInstanceKey, long jobKey) {}
}
