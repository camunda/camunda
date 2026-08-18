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
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
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

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectHistoryBatchWithoutJobKeyOnCreate() {
    // given — CREATE applies the exact same job-context rule as UPDATE (AgentHistoryBatchBehavior
    // is shared, unchanged, between the two processors).
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

    // when — no withJobKey(...) call at all
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-1")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected a job to be provided for the embedded history batch, but no jobKey was "
                + "set. A history batch must be attributed to the active job that produced it.");
  }

  @Test
  public void shouldRejectWholeBatchWhenAnItemIsMissingHistoryItemId() {
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var validItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-user")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    final var invalidItem = new AgentHistoryRecord().setRole(AgentHistoryRole.USER);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(validItem, invalidItem))
            .expectRejection()
            .update();

    // then — the whole batch is rejected, referencing the offending item's index; nothing created
    // (the command was rejected before any AGENT_HISTORY:CREATED event could be appended).
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to add history item at index 1 to agent instance, but historyItemId is "
                + "missing (got empty string). Each history item must have a non-empty "
                + "historyItemId.");
  }

  @Test
  public void shouldRejectWholeBatchWhenRoleUnspecified() {
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var invalidItem = new AgentHistoryRecord().setHistoryItemId("item-1");

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(invalidItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to add history item with historyItemId 'item-1' to agent instance, but its "
                + "role is UNSPECIFIED. Each history item must declare a role.");
  }

  @Test
  public void shouldRejectWholeBatchWhenLoopIterationMissing() {
    // given — the 0 default (loopIteration left unset) is rejected the same as an explicit 0 or a
    // negative value: none of those are valid loopIteration numbers.
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var invalidItem =
        new AgentHistoryRecord().setHistoryItemId("item-1").setRole(AgentHistoryRole.USER);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(invalidItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to add history item with historyItemId 'item-1' to agent instance, but "
                + "loopIteration is missing (got 0). Each history item must declare a positive "
                + "loopIteration.");
  }

  @Test
  public void shouldRejectWholeBatchWhenConfigurationItemHasUnknownChangedAttribute() {
    // given — a CONFIGURATION item naming an attribute this helper doesn't know how to apply (as
    // opposed to a request-level unknown attribute, which is a different check entirely).
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
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
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(invalidItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to update agent instance configuration with history item 'item-config', but "
                + "changedAttributes contained unknown attribute(s) [elementInstanceKey]. Allowed "
                + "attributes are: [maxModelCalls, maxTokens, maxToolCalls, model, provider, "
                + "systemPrompt, tools].");
  }

  @Test
  public void shouldRejectWhenJobNotActive() {
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

    // when — a jobKey that was never activated
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(999999999L)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-1")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to update agent instance related to job with key '999999999', but job was "
                + "not active.");
  }

  @Test
  public void shouldRejectHistoryBatchWithoutJobKey() {
    // given — once a history batch is present, a job context becomes required: the batch's
    // AGENT_HISTORY items must be attributed to the job that produced them.
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

    // when — no withJobKey(...) call at all
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-1")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected a job to be provided for the embedded history batch, but no jobKey was "
                + "set. A history batch must be attributed to the active job that produced it.");
  }

  @Test
  public void shouldRejectWhenJobLeaseMismatch() {
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).withLease().activate();

    // when — carries no lease even though the job has one
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-1")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to update agent instance related to job with key '"
                + jobKey
                + "', but job did not hold the supplied lease. The job may have been "
                + "re-activated.");
  }

  @Test
  public void shouldEmitHistoryEventForEachItemInOrderOnUpdate() {
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var userItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-user")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("Please summarize this document."));
    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("Sure, here is the summary."));
    assistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    final var toolResultItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-tool-result")
            .setRole(AgentHistoryRole.TOOL_RESULT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("lookup result"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(userItem, assistantItem, toolResultItem))
            .update();

    // then — three AGENT_HISTORY:CREATED events, one per item, in array order.
    final var historyEvents =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(agentInstanceKey)
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
              assertThat(e.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);
              assertThat(e.getValue().getJobKey()).isEqualTo(jobKey);
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
  public void shouldAccumulateMetricsAcrossSeparateUpdates() {
    // given — first batch: a single ASSISTANT item with its own token metrics and one tool call.
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var firstAssistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant-1")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("Sure, here is the summary."));
    firstAssistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    firstAssistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    // when — the first update applies the first batch
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(firstAssistantItem))
            .update();

    // then — the first batch's metrics are applied immediately: one model call, one tool call.
    assertThat(firstUpdate.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(firstUpdate.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(firstUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(firstUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(firstUpdate.getValue().getChangedAttributes()).contains("metrics");

    // given — second batch: two more ASSISTANT items, each with their own metrics and tool calls.
    final var secondAssistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant-2")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("Here is more detail."));
    secondAssistantItem.getMetrics().setInputTokens(50L).setOutputTokens(20L);
    secondAssistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-2").setToolName("lookup"));
    secondAssistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-3").setToolName("search"));
    final var thirdAssistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant-3")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("One more detail."));
    thirdAssistantItem.getMetrics().setInputTokens(25L).setOutputTokens(10L);

    // when — the second update applies the second batch on top of the first
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(secondAssistantItem, thirdAssistantItem))
            .update();

    // then — metrics accumulate on top of the first update's totals: two more model calls (three
    // total), two more tool calls from the second item and none from the third (three total), and
    // token counts summed across all three items.
    assertThat(secondUpdate.getValue().getMetrics().getInputTokens()).isEqualTo(100L + 50L + 25L);
    assertThat(secondUpdate.getValue().getMetrics().getOutputTokens()).isEqualTo(40L + 20L + 10L);
    assertThat(secondUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(3);
    assertThat(secondUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(3);
    assertThat(secondUpdate.getValue().getChangedAttributes()).contains("metrics");
  }

  @Test
  public void shouldDeriveModelCallsAndToolCallsFromAssistantItemWithoutExplicitMetrics() {
    // given — an ASSISTANT item carrying no token metrics at all still represents one model call,
    // plus one tool call per entry in its own toolCalls list.
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("Sure, here is the summary."));
    assistantItem.getMetrics().setInputTokens(-1L).setOutputTokens(-1L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-2").setToolName("search"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var toolResultItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-tool-result")
            .setRole(AgentHistoryRole.TOOL_RESULT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("lookup result"));
    toolResultItem.getMetrics().setInputTokens(5L).setOutputTokens(2L);

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var userItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-user")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hello"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(userItem))
            .update();

    // then
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(0);
    assertThat(updated.getValue().getChangedAttributes()).doesNotContain("metrics");
  }

  @Test
  public void shouldApplyInstanceFieldsFromConfigurationItemOnUpdate() {
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
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
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
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
            .withAgentInstanceKey(agentInstanceKey)
            .getFirst();
    assertThat(historyEvent.getValue().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(historyEvent.getValue().getProvider()).isEqualTo("openai");
  }

  @Test
  public void shouldOnlyApplyAttributesNamedInConfigurationItemChangedAttributes() {
    // given — the agent instance is created with model="gpt-4o"/provider="openai" below. The item
    // carries a different value for both, but only names "model" in its own changedAttributes:
    // provider must be left at its original value, since presence alone no longer drives
    // application (that's what tells apart "left as-is" from "deliberately cleared").
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
    configItem.setModel("gpt-4o-mini").setProvider("anthropic");
    configItem.setChangedAttributes(List.of("model"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
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
    // not affect AgentInstance state: only CONFIGURATION items ever do. The instance is created
    // below with model="gpt-4o"/provider="openai"; the item carries different values to prove they
    // never land.
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var userItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-user")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hello"));
    userItem.setModel("gpt-4o-mini").setProvider("anthropic");
    userItem.setChangedAttributes(List.of("model", "provider"));

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    // when — old-style direct metrics delta combined with a history batch in the same request:
    // "metrics" drops out of the allowed set once history is present, so this is rejected exactly
    // like any other unrecognized attribute would be.
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-1")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .withMetricsDelta(10L, 5L, 1, 0)
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to update agent instance, but changedAttributes contained unknown "
                + "attribute(s) [metrics]. Allowed attributes are: [status].");
  }

  @Test
  public void shouldRejectDirectToolsChangeWhenHistoryIsPresent() {
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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    // when — old-style direct tools change combined with a history batch in the same request
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-1")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .withTools(List.of(AgentInstanceClient.tool("calc", "a calculator", "calc-task")))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to update agent instance, but changedAttributes contained unknown "
                + "attribute(s) [tools]. Allowed attributes are: [status].");
  }

  @Test
  public void shouldEmitHistoryEventsOnCreate() {
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
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var userItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-user")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));

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
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId("item-1")
                    .setRole(AgentHistoryRole.USER)
                    .setLoopIteration(1)
                    .addContent(
                        new AgentHistoryMessageContent()
                            .setContentType(AgentHistoryContentType.TEXT)
                            .setText("hi"))))
        .update();

    // when — a second, status-only update carries no batch at all
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .update();

    // then — the previous batch must not leak forward onto an unrelated update. This also proves
    // history never round-trips through primary storage: `current` here was loaded fresh via
    // agentInstanceState.getRecord(), and AgentInstanceCreatedApplier/UpdatedApplier strip history
    // before ever storing a record — if they didn't, the first update's batch would still be
    // sitting in state and would leak back out here.
    assertThat(secondUpdate.getValue().getHistory()).isEmpty();
  }
}
