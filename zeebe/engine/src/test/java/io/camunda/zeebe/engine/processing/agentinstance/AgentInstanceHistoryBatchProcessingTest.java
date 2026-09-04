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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.client.AgentInstanceClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryEmbeddedToolCall;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryMessageContent;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentHistoryIntent;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
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
import java.util.Map;
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
  public void shouldRejectCreateWhenJobLeaseMismatch() {
    // given — unlike UPDATE, CREATE applies a CONFIGURATION item's changes and commits history
    // right away, with no later commit/discard step to catch a stale lease. So CREATE keeps
    // rejecting a stale lease outright instead of accepting it as PENDING (see
    // shouldAcceptUpdateWithSupersededJobLeaseAndAccumulateItsMetrics for the UPDATE behavior).
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

    final var batch1 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var jobIndex1 = batch1.getValue().getJobKeys().indexOf(jobKey);
    final var lease1 = batch1.getValue().getJobs().get(jobIndex1).getLeaseToken();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(helper.getJobType())
        .withLeaseToken(lease1)
        .withRetries(1)
        .fail();

    final var batch2 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobIndex2 = batch2.getValue().getJobKeys().indexOf(jobKey);
    final var lease2 = batch2.getValue().getJobs().get(jobIndex2).getLeaseToken();
    assertThat(lease2).as("re-activation must advance the lease token").isNotEqualTo(lease1);

    // when — the create is sent under lease1, which the job no longer holds
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withJobLease(lease1)
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
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to update agent instance related to job with key '%d', but job did not "
                    .formatted(jobKey)
                + "hold the supplied lease. The job may have been re-activated.");
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithAssistantRole() {
    // given — CREATE restricts history items to CONFIGURATION and USER roles; every other role
    // is rejected (UNSPECIFIED is rejected separately, by the shared validateHistory check, which
    // runs before this CREATE-only check).
    final var role = AgentHistoryRole.ASSISTANT;
    final var allowedRoles = List.of(AgentHistoryRole.CONFIGURATION, AgentHistoryRole.USER);
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
    final var item =
        new AgentHistoryRecord()
            .setHistoryItemId("item-" + role)
            .setRole(role)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("content"));

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(item))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            ("Expected to create agent instance with history item '%s', but its role is '%s'. "
                    + "Allowed roles are: %s.")
                .formatted(item.getHistoryItemId(), role, allowedRoles));
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithToolResultRole() {
    // given — CREATE restricts history items to CONFIGURATION and USER roles; every other role
    // is rejected (UNSPECIFIED is rejected separately, by the shared validateHistory check, which
    // runs before this CREATE-only check).
    final var role = AgentHistoryRole.TOOL_RESULT;
    final var allowedRoles = List.of(AgentHistoryRole.CONFIGURATION, AgentHistoryRole.USER);
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
    final var item =
        new AgentHistoryRecord()
            .setHistoryItemId("item-" + role)
            .setRole(role)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("content"));

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(item))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            ("Expected to create agent instance with history item '%s', but its role is '%s'. "
                    + "Allowed roles are: %s.")
                .formatted(item.getHistoryItemId(), role, allowedRoles));
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithUnspecifiedRole() {
    // given — UNSPECIFIED is rejected by the shared validateHistory check, which runs before
    // this CREATE-only check; pins that ordering so the CREATE-only check never masks it.
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
    final var item =
        new AgentHistoryRecord()
            .setHistoryItemId("item-unspecified")
            .setRole(AgentHistoryRole.UNSPECIFIED)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("content"));

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(item))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            AgentHistoryBatchBehavior.ERROR_MSG_ROLE_UNSPECIFIED.formatted("item-unspecified"));
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithNonZeroInputTokensOnUserItem() {
    // given — an allowed role (USER) is still rejected on CREATE if it carries metrics: metrics
    // are only ever meaningful on ASSISTANT/TOOL_RESULT items, which CREATE already disallows
    // entirely, so a USER/CONFIGURATION item reporting metrics is always a caller mistake.
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
    userItem.getMetrics().setInputTokens(5L);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent instance with history item 'item-user', but it carries "
                + "non-zero token-usage metrics. History items included when creating an agent "
                + "instance must not carry non-zero token-usage metrics; durationMs is exempt.");
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithNonZeroOutputTokensOnUserItem() {
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
    userItem.getMetrics().setOutputTokens(5L);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent instance with history item 'item-user', but it carries "
                + "non-zero token-usage metrics. History items included when creating an agent "
                + "instance must not carry non-zero token-usage metrics; durationMs is exempt.");
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithNonZeroReasoningTokenCountOnUserItem() {
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
    userItem.getMetrics().setReasoningTokenCount(5L);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent instance with history item 'item-user', but it carries "
                + "non-zero token-usage metrics. History items included when creating an agent "
                + "instance must not carry non-zero token-usage metrics; durationMs is exempt.");
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithNonZeroCacheCreationTokenCountOnUserItem() {
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
    userItem.getMetrics().setCacheCreationTokenCount(5L);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent instance with history item 'item-user', but it carries "
                + "non-zero token-usage metrics. History items included when creating an agent "
                + "instance must not carry non-zero token-usage metrics; durationMs is exempt.");
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithNonZeroCacheReadTokenCountOnUserItem() {
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
    userItem.getMetrics().setCacheReadTokenCount(5L);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent instance with history item 'item-user', but it carries "
                + "non-zero token-usage metrics. History items included when creating an agent "
                + "instance must not carry non-zero token-usage metrics; durationMs is exempt.");
  }

  @Test
  public void shouldRejectCreateHistoryBatchWithNegativeInputTokensOnUserItem() {
    // given — inputTokens defaults to -1 to mean "not provided"; any other negative value is not
    // a valid token count and must still be rejected, not silently accepted because it isn't > 0.
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
    userItem.getMetrics().setInputTokens(-2L);

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .isEqualTo(
            "Expected to create agent instance with history item 'item-user', but it carries "
                + "non-zero token-usage metrics. History items included when creating an agent "
                + "instance must not carry non-zero token-usage metrics; durationMs is exempt.");
  }

  @Test
  public void shouldAllowCreateHistoryBatchWithPositiveDurationMsOnUserItem() {
    // given — durationMs isn't an accumulated conversation metric like the others, so it's
    // exempt from the metrics check and may be positive even on a USER/CONFIGURATION item.
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
    userItem.getMetrics().setDurationMs(5L);

    // when
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(userItem))
            .create();

    // then
    assertThat(created.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(created.getValue().getHistory()).hasSize(1);
  }

  @Test
  public void shouldAllowCreateHistoryBatchWithConfigurationAndUserRolesWithoutMetrics() {
    // given — the positive case: CONFIGURATION and USER items with no metrics are exactly what
    // CREATE is meant to accept.
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
    final var configurationItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-configuration")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setChangedAttributes(List.of("model"));
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
            .withJobKey(jobKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withHistory(List.of(configurationItem, userItem))
            .create();

    // then
    assertThat(created.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(created.getValue().getHistory()).hasSize(2);
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
  public void shouldRejectWholeBatchWhenConfigurationItemHasEmptyChangedAttributes() {
    // given — a CONFIGURATION item that names no attribute at all, which would otherwise be
    // accepted as a no-op
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
            .setLoopIteration(1);

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
                + "changedAttributes was empty. A CONFIGURATION item must name at least one "
                + "attribute it changes.");
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
  public void shouldAcceptUpdateWithSupersededJobLeaseAndAccumulateItsMetrics() {
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

    final var batch1 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var jobIndex1 = batch1.getValue().getJobKeys().indexOf(jobKey);
    final var lease1 = batch1.getValue().getJobs().get(jobIndex1).getLeaseToken();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(helper.getJobType())
        .withLeaseToken(lease1)
        .withRetries(1)
        .fail();

    final var batch2 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobIndex2 = batch2.getValue().getJobKeys().indexOf(jobKey);
    final var lease2 = batch2.getValue().getJobs().get(jobIndex2).getLeaseToken();
    assertThat(lease2).as("re-activation must advance the lease token").isNotEqualTo(lease1);

    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-stale-lease")
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
            .setHistoryItemId("item-config-stale-lease")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
    configItem.setModel("gpt-4o-mini").setProvider("azure-openai");
    configItem.setChangedAttributes(List.of("model", "provider"));

    // when — the update is sent under lease1, which the job no longer holds
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(lease1)
            .withHistory(List.of(assistantItem, configItem))
            .update();

    // then
    assertThat(updated.getIntent()).isEqualTo(AgentInstanceIntent.UPDATED);
    assertThat(updated.getValue().getElementInstanceKey()).isEqualTo(elementInstanceKey);

    // and — its metrics are accumulated immediately onto the live agent instance, regardless of
    // which lease the item arrived under.
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(1);

    // and — the CONFIGURATION item is queued, not applied.
    assertThat(updated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(updated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(updated.getValue().getChangedAttributes()).doesNotContain("model", "provider");

    // and — the history item itself is only recorded PENDING under its own (stale) lease: it is
    // not committed as part of this update, since committing is reserved for the job's current
    // (winning) lease.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.COMMITTED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-stale-lease"))
                .exists())
        .as("an item pending under a stale lease is never committed by that same update")
        .isFalse();
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
  public void shouldApplyInstanceFieldsFromConfigurationItemOnlyOnCommit() {
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
    configItem.setModel("gpt-4o-mini").setProvider("azure-openai");
    configItem.addSystemPrompt(
        new AgentHistoryMessageContent()
            .setContentType(AgentHistoryContentType.TEXT)
            .setText("You are a specialized agent."));
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

    // then — the item is only queued, not yet applied: the live fields still reflect the
    // instance's definition at creation, so the instance always has a valid definition even
    // while this CONFIGURATION item is pending.
    assertThat(updated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(updated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(updated.getValue().getDefinition().getSystemPrompt())
        .hasSize(1)
        .first()
        .satisfies(block -> assertThat(block.getText()).isEqualTo("You are a helpful agent."));
    assertThat(updated.getValue().getTools()).isEmpty();
    assertThat(updated.getValue().getChangedAttributes()).isEmpty();

    // the persisted AGENT_HISTORY event is still a full copy of the item, including these fields.
    final var historyItem =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(agentInstanceKey)
            .getFirst();
    assertThat(historyItem.getValue().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(historyItem.getValue().getProvider()).isEqualTo("azure-openai");

    // when — the item is committed
    final var committed =
        ENGINE.agentHistories().withAgentInstanceKey(agentInstanceKey).withJobKey(jobKey).commit();
    assertThat(committed.getIntent()).isEqualTo(AgentHistoryIntent.COMMITTED);

    // then — only now are the live fields driven by the item's own changedAttributes. Skip the
    // UPDATED event from the update command itself (queuing the item, no config applied yet) to
    // reach the one commit() causes.
    final var instanceUpdated =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .skip(1)
            .getFirst();
    assertThat(instanceUpdated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(instanceUpdated.getValue().getDefinition().getProvider()).isEqualTo("azure-openai");
    assertThat(instanceUpdated.getValue().getDefinition().getSystemPrompt())
        .hasSize(1)
        .first()
        .satisfies(block -> assertThat(block.getText()).isEqualTo("You are a specialized agent."));
    assertThat(instanceUpdated.getValue().getTools()).extracting("name").containsExactly("calc");
    assertThat(instanceUpdated.getValue().getLimits().getMaxTokens()).isEqualTo(5000L);
    assertThat(instanceUpdated.getValue().getLimits().getMaxModelCalls()).isEqualTo(8);
    assertThat(instanceUpdated.getValue().getLimits().getMaxToolCalls()).isEqualTo(16);
    assertThat(instanceUpdated.getValue().getChangedAttributes())
        .containsExactlyInAnyOrder(
            "systemPrompt",
            "model",
            "provider",
            "tools",
            "maxTokens",
            "maxModelCalls",
            "maxToolCalls");
  }

  @Test
  public void shouldOnlyApplyAttributesNamedInConfigurationItemChangedAttributesOnCommit() {
    // given — the agent instance is created with model="gpt-4o"/provider="openai" below. The item
    // carries a different value for both, but only names "model" in its own changedAttributes:
    // provider must be left at its original value once committed, since presence alone no longer
    // drives application (that's what tells apart "left as-is" from "deliberately cleared").
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

    // then — nothing is applied yet, the item is only queued
    assertThat(updated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(updated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(updated.getValue().getChangedAttributes()).isEmpty();

    // when — the item is committed
    ENGINE.agentHistories().withAgentInstanceKey(agentInstanceKey).withJobKey(jobKey).commit();

    // then — only "model" lands, "provider" is left at its original value. Skip the UPDATED
    // event from the update command itself (queuing the item, no config applied yet).
    final var instanceUpdated =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .skip(1)
            .getFirst();
    assertThat(instanceUpdated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(instanceUpdated.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(instanceUpdated.getValue().getChangedAttributes()).containsExactly("model");
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
  public void shouldApplyInstanceFieldsFromConfigurationItemImmediatelyOnCreate() {
    // given — unlike UPDATE (where a CONFIGURATION item stays pending until it is committed),
    // CREATE applies it right away, so the instance is guaranteed to start with a valid
    // definition instead of the placeholder one on its CREATE command.
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
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1);
    configItem.setModel("gpt-4o-mini").setProvider("azure-openai");
    configItem.setTools(List.of(new AgentInstanceTool().setName("calc").setElementId("calc-task")));
    configItem.getLimits().setMaxTokens(5000L).setMaxModelCalls(8).setMaxToolCalls(16);
    configItem.setChangedAttributes(
        List.of("model", "provider", "tools", "maxTokens", "maxModelCalls", "maxToolCalls"));

    // when — the initial definition below ("gpt-4o"/"openai") is a placeholder immediately
    // overridden by the CONFIGURATION item in the same CREATE command
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(configItem))
            .create();

    // then
    assertThat(created.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(created.getValue().getDefinition().getProvider()).isEqualTo("azure-openai");
    assertThat(created.getValue().getTools()).extracting("name").containsExactly("calc");
    assertThat(created.getValue().getLimits().getMaxTokens()).isEqualTo(5000L);
    assertThat(created.getValue().getLimits().getMaxModelCalls()).isEqualTo(8);
    assertThat(created.getValue().getLimits().getMaxToolCalls()).isEqualTo(16);
    // CREATED has no prior state to diff against, so changedAttributes stays empty regardless
    // (same as for a non-CONFIGURATION item, see shouldEmitHistoryEventsOnCreate above).
    assertThat(created.getValue().getChangedAttributes()).isEmpty();

    final var historyEvent =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(created.getKey())
            .getFirst();
    assertThat(historyEvent.getValue().getModel()).isEqualTo("gpt-4o-mini");
  }

  @Test
  public void shouldCommitCreateConfigurationItemImmediatelyPreventingDiscard() {
    // given — a CONFIGURATION item submitted with CREATE, same as
    // shouldApplyInstanceFieldsFromConfigurationItemImmediatelyOnCreate above.
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
    // content/toolCalls are conversation-payload fields that a CONFIGURATION item should never
    // carry in practice, but setting them here proves the COMMITTED event is genuinely built by
    // reading the trimmed record back from state — reusing the untrimmed in-memory `item` instead
    // (a regression this test would otherwise miss) would leak them straight through.
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"))
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("should be stripped"))
            .setToolCalls(
                List.of(
                    new AgentHistoryEmbeddedToolCall()
                        .setToolCallId("call-1")
                        .setToolName("should-be-stripped")));

    // when
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(configItem))
            .create();

    // then — the item is already COMMITTED as part of CREATE itself, never left PENDING under
    // the job.
    final var committed =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withAgentInstanceKey(created.getKey())
            .getFirst();
    assertThat(committed.getValue().getHistoryItemId()).isEqualTo("item-config");
    // trimmed at primary-storage insert, same as every other role — proves the COMMITTED event was
    // built by reading the item back from state rather than re-emitting the untrimmed in-memory
    // one.
    assertThat(committed.getValue().getContent()).isEmpty();
    assertThat(committed.getValue().getToolCalls()).isEmpty();

    // when — the creating job is discarded afterwards (e.g. abandoned before it ever completes)
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(jobKey)
            .agentHistory(AgentHistoryIntent.DISCARD, new AgentHistoryRecord().setJobKey(jobKey)));
    final long clockResetKey = ENGINE.clock().reset().getKey();

    // then — nothing is left pending to discard, so no DISCARDED event is ever produced for this
    // item: it can never be erased from history, unlike before this fix. Scoped to this item's
    // historyItemId (rather than "no DISCARDED at all") so the assertion stays correct if this
    // batch is ever extended with another, still-discardable item.
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getKey() == clockResetKey)
                .withValueType(ValueType.AGENT_HISTORY)
                .withIntent(AgentHistoryIntent.DISCARDED)
                .filter(
                    r ->
                        ((AgentHistoryRecordValue) r.getValue())
                            .getHistoryItemId()
                            .equals("item-config"))
                .exists())
        .isFalse();
  }

  @Test
  public void shouldOnlyCommitConfigurationItemFromMixedCreateBatch() {
    // given — a CREATE batch with both a CONFIGURATION item (committed immediately by this fix)
    // and a plain USER item (which must stay PENDING/discardable, unaffected by the fix).
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
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));
    final var userItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-user")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1);

    // when
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(configItem, userItem))
            .create();

    // then — only the CONFIGURATION item is committed as part of CREATE itself.
    final var committed =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withAgentInstanceKey(created.getKey())
            .getFirst();
    assertThat(committed.getValue().getHistoryItemId()).isEqualTo("item-config");

    final var userHistoryKey =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(created.getKey())
            .filter(r -> r.getValue().getHistoryItemId().equals("item-user"))
            .getFirst()
            .getKey();

    // when — the job is discarded before it ever completes
    final var discarded = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // then — only the still-pending USER item is discarded; the already-committed CONFIGURATION
    // item is untouched (it is no longer in the pending column family the discard visits).
    assertThat(discarded.getIntent()).isEqualTo(AgentHistoryIntent.DISCARDED);
    assertThat(discarded.getKey()).isEqualTo(userHistoryKey);
  }

  @Test
  public void shouldCommitEachConfigurationItemFromCreateBatchWithMultipleConfigItems() {
    // given — a CREATE batch with two CONFIGURATION items (one changing the model, one changing
    // tools). Both must be applied and committed, not just the first or the last.
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
    final var modelItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config-model")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));
    final var toolsItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config-tools")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(2)
            .setTools(List.of(new AgentInstanceTool().setName("calc").setElementId("calc-task")))
            .setChangedAttributes(List.of("tools"));

    // when
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(modelItem, toolsItem))
            .create();

    // then — both items' effects are applied to the instance...
    assertThat(created.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
    assertThat(created.getValue().getTools()).extracting("name").containsExactly("calc");

    // ...and both items are committed, not just applied.
    final var committedIds =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
            .withAgentInstanceKey(created.getKey())
            .limit(2)
            .map(r -> r.getValue().getHistoryItemId())
            .toList();
    assertThat(committedIds).containsExactlyInAnyOrder("item-config-model", "item-config-tools");
  }

  @Test
  public void shouldDedupUpdateAgainstCreateCommittedConfigurationItemAcrossLeases() {
    // given — CREATE's own CONFIGURATION item commits immediately (see
    // shouldCommitCreateConfigurationItemImmediatelyPreventingDiscard above), registering its
    // historyItemId as committed for this agent instance.
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
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));

    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(configItem))
            .create();
    final var agentInstanceKey = created.getKey();

    // when — a later UPDATE, under a different lease on the same job (simulating a worker retry
    // after the original CREATE-time activation was superseded), resends the same historyItemId
    // with a DIFFERENT model value.
    final var resentConfigItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-nano")
            .setChangedAttributes(List.of("model"));
    final var update =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease("retry-lease")
            .withHistory(List.of(resentConfigItem))
            .update();

    // then — recognized as a duplicate of the already-committed CREATE-time item (matched via
    // getCommittedHistoryItemKey, since a different lease means the pending-items lookup alone
    // would not have matched it), so its own "gpt-4o-nano" payload is never applied.
    assertThat(update.getValue().getHistory().get(0).isDuplicate()).isTrue();
    assertThat(update.getValue().getChangedAttributes()).doesNotContain("model");
    assertThat(update.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
  }

  @Test
  public void shouldStillAllowDiscardOfNonConfigurationItemsFromCreateBatch() {
    // given — a plain USER item submitted with CREATE, unaffected by this fix: only CONFIGURATION
    // items from CREATE's batch commit immediately, every other role stays PENDING as before.
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
            .setLoopIteration(1);

    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withJobKey(jobKey)
            .withHistory(List.of(userItem))
            .create();
    final var userHistoryKey =
        RecordingExporter.agentHistoryRecords(AgentHistoryIntent.CREATED)
            .withAgentInstanceKey(created.getKey())
            .getFirst()
            .getKey();

    // when — the job is discarded before it ever commits
    final var discarded = ENGINE.agentHistories().withJobKey(jobKey).discard();

    // then — the plain USER item is discarded exactly as before this fix
    assertThat(discarded.getIntent()).isEqualTo(AgentHistoryIntent.DISCARDED);
    assertThat(discarded.getKey()).isEqualTo(userHistoryKey);
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

  @Test
  public void shouldMarkResentItemAsDuplicateWithinSameJobActivation() {
    // given — an item is created once, then the same historyItemId is resent on a second UPDATE
    // to the same still-activated job (e.g. an HTTP client retry).
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

    // when — first submission creates the item
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-user")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();
    final var originalKey = firstUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    // when — the same historyItemId is resent, with different content, on a second UPDATE to the
    // same job (content differs on purpose: dedup keys on historyItemId alone, not on content).
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-user")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi again"))))
            .update();

    // then — the batch is still accepted, but the resent item is echoed back flagged as a
    // duplicate of the original, not as a new entry.
    final var echoed = secondUpdate.getValue().getHistory();
    assertThat(echoed).hasSize(1);
    assertThat(echoed.get(0).isDuplicate()).isTrue();
    assertThat(echoed.get(0).getAgentHistoryKey()).isEqualTo(originalKey);

    // no second AGENT_HISTORY:CREATED event was appended for "item-user" — bounded via a clock
    // reset as a sentinel so the check cannot hang waiting for a record that must never arrive.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    final var createdForItem =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.CREATED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue()).getHistoryItemId().equals("item-user"))
            .toList();
    assertThat(createdForItem).hasSize(1);
  }

  @Test
  public void shouldNotReaccumulateMetricsForDuplicateAssistantItem() {
    // given — an ASSISTANT item with token metrics and a tool call is applied once.
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
    assistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    assistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));

    // when — first submission applies the metrics
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(assistantItem))
            .update();
    assertThat(firstUpdate.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(firstUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(firstUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(1);

    // when — the exact same item is resent on a second UPDATE to the same job
    final var resentAssistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("Sure, here is the summary."));
    resentAssistantItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    resentAssistantItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(resentAssistantItem))
            .update();

    // then — the duplicate must not be counted a second time: metrics stay exactly as after the
    // first submission.
    assertThat(secondUpdate.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(secondUpdate.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(secondUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(secondUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(1);
  }

  @Test
  public void shouldNotApplyChangedAttributesFromDuplicateConfigurationItemOnCommit() {
    // given — a CONFIGURATION item queues a model change. It is not applied on the update itself
    // — that only happens on commit — so the duplicate check here can only be proven by what
    // commit ends up applying.
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
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));

    // when — first submission queues the model change; nothing is applied yet.
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(configItem))
            .update();
    assertThat(firstUpdate.getValue().getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(firstUpdate.getValue().getChangedAttributes()).isEmpty();

    // when — the same historyItemId is resent on a second UPDATE to the same job, this time
    // carrying a DIFFERENT model value. If the duplicate's own payload were ever applied, commit
    // below would end up with "gpt-4o-nano" instead of the original item's "gpt-4o-mini".
    final var resentConfigItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-nano")
            .setChangedAttributes(List.of("model"));
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(resentConfigItem))
            .update();
    assertThat(secondUpdate.getValue().getChangedAttributes()).doesNotContain("model");
    assertThat(secondUpdate.getValue().getHistory().get(0).isDuplicate()).isTrue();

    // when — the single, deduped item is committed
    ENGINE.agentHistories().withAgentInstanceKey(agentInstanceKey).withJobKey(jobKey).commit();

    // then — only the original item's value is applied; the duplicate's payload never took
    // effect. Skip the two UPDATED events from the update commands themselves (queuing only, no
    // config applied yet) to reach the one commit() causes.
    final var instanceUpdated =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .skip(2)
            .getFirst();
    assertThat(instanceUpdated.getValue().getDefinition().getModel()).isEqualTo("gpt-4o-mini");
  }

  @Test
  public void shouldDedupUniformlyAcrossAllRoles() {
    // given — one item per role (USER, ASSISTANT, TOOL_RESULT, CONFIGURATION) is created.
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
                    .setText("hi"));
    final var assistantItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-assistant")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hello back"));
    final var toolResultItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-tool-result")
            .setRole(AgentHistoryRole.TOOL_RESULT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("lookup result"));
    final var configItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-config")
            .setRole(AgentHistoryRole.CONFIGURATION)
            .setLoopIteration(1)
            .setModel("gpt-4o-mini")
            .setChangedAttributes(List.of("model"));

    // when — first submission creates all four items
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(userItem, assistantItem, toolResultItem, configItem))
            .update();
    final var originalKeys =
        firstUpdate.getValue().getHistory().stream()
            .map(AgentHistoryRecordValue::getAgentHistoryKey)
            .toList();

    // when — the same four historyItemIds are resent, in the same order, on a second UPDATE
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-user")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi")),
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-assistant")
                        .setRole(AgentHistoryRole.ASSISTANT)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hello back")),
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-tool-result")
                        .setRole(AgentHistoryRole.TOOL_RESULT)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("lookup result")),
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-config")
                        .setRole(AgentHistoryRole.CONFIGURATION)
                        .setLoopIteration(1)
                        .setModel("gpt-4o-mini")
                        .setChangedAttributes(List.of("model"))))
            .update();

    // then — every role dedups the same way: all four echoed entries are flagged duplicates of
    // their respective originals, regardless of role.
    final var echoed = secondUpdate.getValue().getHistory();
    assertThat(echoed).hasSize(4);
    assertThat(echoed)
        .extracting(AgentHistoryRecordValue::isDuplicate)
        .containsExactly(true, true, true, true);
    assertThat(echoed)
        .extracting(AgentHistoryRecordValue::getAgentHistoryKey)
        .containsExactlyElementsOf(originalKeys);
  }

  @Test
  public void shouldFlagOnlyAlreadySeenItemsInMixedBatchPreservingOrder() {
    // given — two items ("item-a", "item-b") are created on a first UPDATE.
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
    final var itemA =
        new AgentHistoryRecord()
            .setHistoryItemId("item-a")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("first"));
    final var itemB =
        new AgentHistoryRecord()
            .setHistoryItemId("item-b")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("second"));
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(itemA, itemB))
            .update();
    final var keyA = firstUpdate.getValue().getHistory().get(0).getAgentHistoryKey();
    final var keyB = firstUpdate.getValue().getHistory().get(1).getAgentHistoryKey();

    // when — a second UPDATE mixes the two already-seen items with one brand-new item
    // ("item-c"), deliberately out of creation order: [b, c, a].
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-b")
                        .setRole(AgentHistoryRole.ASSISTANT)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("second")),
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-c")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("third")),
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-a")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("first"))))
            .update();

    // then — exactly one entry per submitted item, in request order: [b (dup), c (new), a (dup)].
    final var echoed = secondUpdate.getValue().getHistory();
    assertThat(echoed).hasSize(3);
    assertThat(echoed)
        .extracting(AgentHistoryRecordValue::getHistoryItemId)
        .containsExactly("item-b", "item-c", "item-a");
    assertThat(echoed.get(0).isDuplicate()).isTrue();
    assertThat(echoed.get(0).getAgentHistoryKey()).isEqualTo(keyB);
    assertThat(echoed.get(1).isDuplicate()).isFalse();
    assertThat(echoed.get(2).isDuplicate()).isTrue();
    assertThat(echoed.get(2).getAgentHistoryKey()).isEqualTo(keyA);

    // exactly one new AGENT_HISTORY:CREATED event was appended by the second UPDATE, for
    // "item-c" only.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    final var newCreatedEvents =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.CREATED)
            .filter(r -> r.getSourceRecordPosition() == secondUpdate.getSourceRecordPosition())
            .toList();
    assertThat(newCreatedEvents).hasSize(1);
    assertThat(((AgentHistoryRecordValue) newCreatedEvents.get(0).getValue()).getHistoryItemId())
        .isEqualTo("item-c");
  }

  @Test
  public void shouldRejectWholeBatchWhenTwoItemsShareHistoryItemIdWithinSameRequest() {
    // given — a single request carries two items with the same historyItemId. This is a
    // malformed request, distinct from a cross-request retry: nothing may be created at all.
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
    final var firstItem =
        new AgentHistoryRecord()
            .setHistoryItemId("dup-id")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("first"));
    final var secondItem =
        new AgentHistoryRecord()
            .setHistoryItemId("dup-id")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("second"));

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(List.of(firstItem, secondItem))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains(
            """
            Expected to create or update agent instance history, but historyItemId 'dup-id' is used \
            by more than one history item. Each history item must have a unique historyItemId.""");

    // nothing was created at all — bounded via a clock reset sentinel.
    final var clockResetKey = ENGINE.clock().reset().getKey();
    final var createdEvents =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.CREATED)
            .filter(
                r -> ((AgentHistoryRecordValue) r.getValue()).getHistoryItemId().equals("dup-id"))
            .toList();
    assertThat(createdEvents).isEmpty();
  }

  @Test
  public void shouldRejectUpdateFromSecondActiveElementInstanceLinkedToSameAgentInstance() {
    // given — a parallel multi-instance AI-agent service task produces two element instances,
    // EI1 and EI2, active at the same time, sharing the same elementId and process instance.
    final var multiInstanceProcessId = "dedup-parallel-multi-instance";
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
                                    m.zeebeInputCollectionExpression("items")
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
    final var children =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList();
    final var ei1 = children.get(0).getKey();
    final var ei2 = children.get(1).getKey();

    // the agent instance is created on EI1; EI1 remains active (parallel multi-instance).
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();

    ENGINE.jobs().withType(helper.getJobType()).withMaxJobsToActivate(2).activate();
    final var job2Key =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .filter(r -> r.getValue().getElementInstanceKey() == ei2)
            .getFirst()
            .getKey();

    // when — EI2, a second, still-active element instance, attempts to push a history batch to
    // the same agent instance while EI1 (the current writer) has not completed.
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withJobKey(job2Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-from-ei2")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .expectRejection()
            .update();

    // then — only one element instance may write to a given agent instance at a time.
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(
            """
            Expected to update agent instance with key '%d' for element instance with key '%d', \
            but element instance with key '%d' is still the active writer for this agent instance \
            and has not completed. Only one element instance may write to a given agent instance \
            at a time."""
                .formatted(agentInstanceKey, ei2, ei1));
  }

  @Test
  public void shouldAcceptPushFromSecondElementInstanceWhenFirstWritersJobHasFailed() {
    // given — same setup as the rejection case above: a parallel multi-instance AI-agent service
    // task produces two element instances, EI1 and EI2, both still active.
    final var multiInstanceProcessId = "dedup-parallel-multi-instance-failed-writer";
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
                                    m.zeebeInputCollectionExpression("items")
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
    final var children =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList();
    final var ei1 = children.get(0).getKey();
    final var ei2 = children.get(1).getKey();

    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();

    ENGINE.jobs().withType(helper.getJobType()).withMaxJobsToActivate(2).activate();
    final var activatedJobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .limit(2)
            .toList();
    final var job1Key =
        activatedJobs.stream()
            .filter(r -> r.getValue().getElementInstanceKey() == ei1)
            .findFirst()
            .orElseThrow()
            .getKey();
    final var job2Key =
        activatedJobs.stream()
            .filter(r -> r.getValue().getElementInstanceKey() == ei2)
            .findFirst()
            .orElseThrow()
            .getKey();

    // EI1's job fails with no retries left, raising an incident. EI1 itself stays active — job
    // failure and element-instance completion are independent, so the element instance does not
    // reflect that its writer is done.
    ENGINE.job().withKey(job1Key).withRetries(0).fail();

    // when — EI2 pushes a history batch to the same agent instance. EI1 is still active, but its
    // job is no longer ACTIVATED, so EI1 can no longer be mid-write.
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withJobKey(job2Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-from-ei2")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();

    // then — accepted: a failed job cannot still be mid-write, regardless of its element
    // instance's activity.
    assertThat(updated.getIntent()).isEqualTo(AgentInstanceIntent.UPDATED);
  }

  @Test
  public void
      shouldRejectStatusOnlyUpdateFromSecondActiveElementInstanceWithoutDisturbingFirstWritersRetry() {
    // given — same parallel multi-instance setup: EI1 and EI2 both linked to one agent instance,
    // both jobs ACTIVATED. EI1 pushes history first, so it is the current writer.
    final var multiInstanceProcessId = "dedup-parallel-multi-instance-status-only-bystander";
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
                                    m.zeebeInputCollectionExpression("items")
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
    final var children =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList();
    final var ei1 = children.get(0).getKey();
    final var ei2 = children.get(1).getKey();

    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();

    ENGINE.jobs().withType(helper.getJobType()).withMaxJobsToActivate(2).activate();
    final var activatedJobs =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .limit(2)
            .toList();
    final var job1Key =
        activatedJobs.stream()
            .filter(r -> r.getValue().getElementInstanceKey() == ei1)
            .findFirst()
            .orElseThrow()
            .getKey();

    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(ei1)
        .withJobKey(job1Key)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId("item-from-ei1")
                    .setRole(AgentHistoryRole.USER)
                    .setLoopIteration(1)
                    .addContent(
                        new AgentHistoryMessageContent()
                            .setContentType(AgentHistoryContentType.TEXT)
                            .setText("hi"))))
        .update();

    // when — EI2, a different, still-active element instance whose job is still ACTIVATED, sends
    // a status-only update while EI1 is still the writer.
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then — rejected: a status-only update gets no exemption from the single-active-writer
    // rule. Carrying no history doesn't change that EI2 is a different, still-active element
    // instance touching a writer role it doesn't hold.
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(
            """
            Expected to update agent instance with key '%d' for element instance with key '%d', \
            but element instance with key '%d' is still the active writer for this agent instance \
            and has not completed. Only one element instance may write to a given agent instance \
            at a time."""
                .formatted(agentInstanceKey, ei2, ei1));

    // and — EI1, the real writer, still succeeds on its own retry (e.g. an HTTP retry after a
    // timeout): the rejected bystander did not corrupt anything for it.
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei1)
            .withJobKey(job1Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-from-ei1-retry")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi again"))))
            .update();
    assertThat(updated.getIntent()).isEqualTo(AgentInstanceIntent.UPDATED);
  }

  @Test
  public void shouldNotTreatItemPendingUnderDifferentLeaseAsDuplicate() {
    // given — a lease marks one activation attempt. Only the committing lease's pending items
    // may ever survive, so matching an item pending under a DIFFERENT lease as a duplicate would
    // falsely erase an item that is about to be legitimately recreated under the winning lease.
    // Activation 1 (superseded): push an item under lease1, then fail to trigger re-activation.
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

    final var batch1 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var jobIndex1 = batch1.getValue().getJobKeys().indexOf(jobKey);
    final var lease1 = batch1.getValue().getJobs().get(jobIndex1).getLeaseToken();

    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(elementInstanceKey)
        .withJobKey(jobKey)
        .withJobLease(lease1)
        .withHistory(
            List.of(
                new AgentHistoryRecord()
                    .setHistoryItemId("item-cross-lease")
                    .setRole(AgentHistoryRole.USER)
                    .setLoopIteration(1)
                    .addContent(
                        new AgentHistoryMessageContent()
                            .setContentType(AgentHistoryContentType.TEXT)
                            .setText("hi"))))
        .update();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(helper.getJobType())
        .withLeaseToken(lease1)
        .withRetries(1)
        .fail();

    // Activation 2 (winning): re-activate under a new lease, then resend the same historyItemId.
    final var batch2 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobIndex2 = batch2.getValue().getJobKeys().indexOf(jobKey);
    final var lease2 = batch2.getValue().getJobs().get(jobIndex2).getLeaseToken();

    // when
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(lease2)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-cross-lease")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();

    // then — the item pending under lease1 must not be treated as a duplicate of this one.
    assertThat(secondUpdate.getValue().getHistory().get(0).isDuplicate()).isFalse();
    assertThat(lease2).as("re-activation must advance the lease token").isNotEqualTo(lease1);
  }

  @Test
  public void shouldAccumulateMetricsOnceForSameItemIdPendingUnderTwoLeases() {
    // given — the same historyItemId arrives under a second lease while the first lease's copy
    // is still pending (the job has not completed, so neither copy is committed or discarded
    // yet). The metrics-accumulated-ids mechanism keys on historyItemId alone, so it must
    // recognize the second copy as the same id and skip re-accumulating its metrics, even though
    // the two copies live under different leases and neither has won yet.
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

    // Activation 1 (superseded): push the item under lease1, carrying non-zero token/tool-call
    // deltas, then fail the job to trigger re-activation. Its copy stays pending — never
    // committed, never discarded.
    final var batch1 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();
    final var jobIndex1 = batch1.getValue().getJobKeys().indexOf(jobKey);
    final var lease1 = batch1.getValue().getJobs().get(jobIndex1).getLeaseToken();

    final var firstItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-pending-under-two-leases")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    firstItem.getMetrics().setInputTokens(100L).setOutputTokens(40L);
    firstItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-1").setToolName("lookup"));
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(lease1)
            .withHistory(List.of(firstItem))
            .update();
    assertThat(firstUpdate.getValue().getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(firstUpdate.getValue().getMetrics().getOutputTokens()).isEqualTo(40L);
    assertThat(firstUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(firstUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(1);

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(helper.getJobType())
        .withLeaseToken(lease1)
        .withRetries(1)
        .fail();

    // Activation 2: re-activate under a new lease — lease1's copy is still pending, the job has
    // not completed — then resend the same historyItemId with its own (different) deltas.
    final var batch2 = ENGINE.jobs().withType(helper.getJobType()).withLease().activate();
    final var jobIndex2 = batch2.getValue().getJobKeys().indexOf(jobKey);
    final var lease2 = batch2.getValue().getJobs().get(jobIndex2).getLeaseToken();
    assertThat(lease2).as("re-activation must advance the lease token").isNotEqualTo(lease1);

    final var secondItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-pending-under-two-leases")
            .setRole(AgentHistoryRole.ASSISTANT)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    secondItem.getMetrics().setInputTokens(200L).setOutputTokens(80L);
    secondItem.addToolCall(
        new AgentHistoryEmbeddedToolCall().setToolCallId("call-2").setToolName("lookup"));

    // when
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease(lease2)
            .withHistory(List.of(secondItem))
            .update();

    // then — the second copy shares the first copy's historyItemId, so its metrics must not be
    // counted again: totals stay exactly where the first (still-pending) copy left them.
    assertThat(secondUpdate.getValue().getMetrics().getInputTokens())
        .as("item-pending-under-two-leases' metrics were already accumulated under lease1")
        .isEqualTo(100L);
    assertThat(secondUpdate.getValue().getMetrics().getOutputTokens())
        .as("item-pending-under-two-leases' metrics were already accumulated under lease1")
        .isEqualTo(40L);
    assertThat(secondUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(secondUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(secondUpdate.getValue().getChangedAttributes())
        .as("metrics were skipped for the item already accumulated under lease1")
        .doesNotContain("metrics");
  }

  @Test
  public void shouldAcceptPushFromSecondElementInstanceAfterFirstCompletes() {
    // given — a sequential multi-instance AI-agent service task: EI1 activates, completes, then
    // EI2 activates. This is the counter-case to the second-active-writer rejection above: it
    // proves the check is about "still active", not "ever used" for this agent instance.
    final var multiInstanceProcessId = "dedup-sequential-multi-instance";
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

    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var job2Key =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .filter(r -> r.getValue().getElementInstanceKey() == ei2)
            .getFirst()
            .getKey();

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withJobKey(job2Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-from-ei2")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();

    // then — accepted, not rejected: EI1 already completed, so it no longer holds the write lock.
    assertThat(updated.getValue().getHistory()).hasSize(1);
  }

  @Test
  public void shouldMarkResentItemAsDuplicateAcrossJobActivations() {
    // given — a sequential multi-instance AI-agent service task: job1 pushes "item-x" and
    // completes (committing it), then job2 — a brand-new job on a brand-new element instance,
    // for the same agent instance — resends "item-x"
    final var multiInstanceProcessId = "cross-job-dedup-sequential-multi-instance";
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
    final var job1Key =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    // when — job1 pushes "item-x", then completes, committing it
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei1)
            .withJobKey(job1Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-x")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();
    final var originalKey = firstUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    ENGINE.job().ofInstance(processInstanceKey).withType(helper.getJobType()).complete();
    RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
        .withJobKey(job1Key)
        .getFirst();

    final var ei2 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList()
            .get(1)
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var job2Key =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .filter(r -> r.getValue().getElementInstanceKey() == ei2)
            .getFirst()
            .getKey();

    // when — job2 (a brand-new job, on a brand-new element instance) resends "item-x", with
    // different content
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withJobKey(job2Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-x")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi again"))))
            .update();

    // then
    final var echoed = secondUpdate.getValue().getHistory();
    assertThat(echoed).hasSize(1);
    assertThat(echoed.get(0).isDuplicate())
        .as("resent item is flagged a duplicate, not minted as a new entry")
        .isTrue();
    assertThat(echoed.get(0).getAgentHistoryKey()).isEqualTo(originalKey);

    // bounded via a clock-reset sentinel so the check cannot hang on a record that never arrives
    final var clockResetKey = ENGINE.clock().reset().getKey();
    final var createdForItem =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.CREATED)
            .filter(
                r -> ((AgentHistoryRecordValue) r.getValue()).getHistoryItemId().equals("item-x"))
            .toList();
    assertThat(createdForItem).as("no second CREATED event was appended for item-x").hasSize(1);
  }

  @Test
  public void shouldScopeDuplicateDetectionPerAgentInstance() {
    // given — agent instance A: an item is committed via an explicit COMMIT command (the job
    // itself stays ACTIVATED throughout)
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
    final var processInstanceKeyA = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKeyA)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKeyA =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKeyA)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var jobAKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKeyA)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKeyA)
            .withElementInstanceKey(elementInstanceKeyA)
            .withJobKey(jobAKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-shared")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();
    final var originalKey = firstUpdate.getValue().getHistory().get(0).getAgentHistoryKey();
    ENGINE.agentHistories().withJobKey(jobAKey).commit();
    RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
        .withJobKey(jobAKey)
        .getFirst();

    // when — the same historyItemId is resent to agent instance A itself
    final var resendToA =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKeyA)
            .withElementInstanceKey(elementInstanceKeyA)
            .withJobKey(jobAKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-shared")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi again"))))
            .update();

    // then
    assertThat(resendToA.getValue().getHistory().get(0).isDuplicate())
        .as("flagged a duplicate of the original, within A")
        .isTrue();
    assertThat(resendToA.getValue().getHistory().get(0).getAgentHistoryKey())
        .isEqualTo(originalKey);

    // given — a second, unrelated agent instance B
    final var processInstanceKeyB = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKeyB)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKeyB =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKeyB)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var jobBKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKeyB)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    // when — the SAME historyItemId ("item-shared") is sent under agent instance B for the first
    // time
    final var sentToB =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKeyB)
            .withElementInstanceKey(elementInstanceKeyB)
            .withJobKey(jobBKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-shared")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi"))))
            .update();

    // then
    assertThat(sentToB.getValue().getHistory().get(0).isDuplicate())
        .as("not a duplicate: the committed-ids scope is per agent instance, not global")
        .isFalse();
    assertThat(sentToB.getValue().getHistory().get(0).getAgentHistoryKey())
        .isNotEqualTo(originalKey);
  }

  @Test
  public void shouldAccumulateMetricsPerAgentInstanceForSameHistoryItemId() {
    // given — agent instance A receives "item-shared" with non-zero metrics
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
    final var processInstanceKeyA = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyA =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKeyA)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKeyA =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKeyA)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var jobAKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKeyA)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    final var itemForA =
        new AgentHistoryRecord()
            .setHistoryItemId("item-shared")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    itemForA.getMetrics().setInputTokens(10L).setOutputTokens(5L);
    final var updateA =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKeyA)
            .withElementInstanceKey(elementInstanceKeyA)
            .withJobKey(jobAKey)
            .withHistory(List.of(itemForA))
            .update();

    // then — instance A's metrics reflect its own item
    assertThat(updateA.getValue().getMetrics().getInputTokens()).isEqualTo(10L);
    assertThat(updateA.getValue().getMetrics().getOutputTokens()).isEqualTo(5L);

    // given — a second, unrelated agent instance B
    final var processInstanceKeyB = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstanceKeyB =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKeyB)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst()
            .getKey();
    final var agentInstanceKeyB =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKeyB)
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .create()
            .getKey();
    ENGINE.jobs().withType(helper.getJobType()).activate();
    final var jobBKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKeyB)
            .withType(helper.getJobType())
            .getFirst()
            .getKey();

    // when — the SAME historyItemId ("item-shared") is sent to agent instance B for the first
    // time, carrying its own (different) non-zero metrics
    final var itemForB =
        new AgentHistoryRecord()
            .setHistoryItemId("item-shared")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    itemForB.getMetrics().setInputTokens(20L).setOutputTokens(8L);
    final var updateB =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKeyB)
            .withElementInstanceKey(elementInstanceKeyB)
            .withJobKey(jobBKey)
            .withHistory(List.of(itemForB))
            .update();

    // then — instance B accumulates its own metrics; the same historyItemId already accumulated
    // on instance A does not cause B's accumulation to be skipped, because the
    // metrics-accumulated-ids store is keyed by (agentInstanceKey, historyItemId), not by
    // historyItemId alone.
    assertThat(updateB.getValue().getMetrics().getInputTokens())
        .as("instance B accumulates its own metrics for item-shared, unaffected by instance A")
        .isEqualTo(20L);
    assertThat(updateB.getValue().getMetrics().getOutputTokens())
        .as("instance B accumulates its own metrics for item-shared, unaffected by instance A")
        .isEqualTo(8L);
    assertThat(updateB.getValue().getChangedAttributes())
        .as("metrics were not skipped for instance B's first-ever item-shared")
        .contains("metrics");
  }

  @Test
  public void shouldNotTreatDiscardedItemAsDuplicateWhenResent() {
    // given — one job pushes two items under two different leases: "item-committed" under
    // lease-1, "item-discarded" under lease-2. Both carry non-zero token deltas so a later
    // re-accumulation would be visible rather than passing vacuously.
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

    final var committedItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-committed")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hi"));
    committedItem.getMetrics().setInputTokens(10L).setOutputTokens(5L);
    final var firstUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease("lease-1")
            .withHistory(List.of(committedItem))
            .update();
    final var committedOriginalKey =
        firstUpdate.getValue().getHistory().get(0).getAgentHistoryKey();

    final var discardedItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-discarded")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hey"));
    discardedItem.getMetrics().setInputTokens(7L).setOutputTokens(3L);
    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withJobLease("lease-2")
            .withHistory(List.of(discardedItem))
            .update();

    assertThat(secondUpdate.getValue().getMetrics().getInputTokens())
        .describedAs(
            "metrics apply at accept time, not commit time — both items' deltas are already on"
                + " the instance before either is committed or discarded")
        .isEqualTo(10L + 7L);
    assertThat(secondUpdate.getValue().getMetrics().getOutputTokens())
        .describedAs(
            "metrics apply at accept time, not commit time — both items' deltas are already on"
                + " the instance before either is committed or discarded")
        .isEqualTo(5L + 3L);

    // when — COMMIT with lease-1: "item-committed" is committed, "item-discarded" (lease-2, a
    // superseded activation) is discarded
    ENGINE.agentHistories().withJobKey(jobKey).withJobLease("lease-1").commit();
    RecordingExporter.agentHistoryRecords(AgentHistoryIntent.COMMITTED)
        .withJobKey(jobKey)
        .getFirst();
    RecordingExporter.agentHistoryRecords(AgentHistoryIntent.DISCARDED)
        .withJobKey(jobKey)
        .getFirst();

    // when — a later request resends both historyItemIds, this time with different (larger)
    // metrics on the resent "item-discarded" — if it were re-accumulated, the totals below would
    // include it a second time.
    final var resentDiscardedItem =
        new AgentHistoryRecord()
            .setHistoryItemId("item-discarded")
            .setRole(AgentHistoryRole.USER)
            .setLoopIteration(1)
            .addContent(
                new AgentHistoryMessageContent()
                    .setContentType(AgentHistoryContentType.TEXT)
                    .setText("hey again"));
    resentDiscardedItem.getMetrics().setInputTokens(100L).setOutputTokens(50L);
    final var resend =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withJobKey(jobKey)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-committed")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("hi again")),
                    resentDiscardedItem))
            .update();

    // then — the content half: resending still recreates "item-discarded" fresh (it is not a
    // duplicate), exactly as before this change.
    final var echoed = resend.getValue().getHistory();
    assertThat(echoed).hasSize(2);
    assertThat(echoed.get(0).isDuplicate())
        .as("the committed item is flagged a duplicate of the original")
        .isTrue();
    assertThat(echoed.get(0).getAgentHistoryKey()).isEqualTo(committedOriginalKey);
    assertThat(echoed.get(1).isDuplicate())
        .as("the discarded item left no trace, so it is treated as brand new")
        .isFalse();

    // bounded via a clock-reset sentinel so the check cannot hang on a record that never arrives
    final var clockResetKey = ENGINE.clock().reset().getKey();
    final var createdForDiscardedItem =
        RecordingExporter.records()
            .limit(r -> r.getKey() == clockResetKey)
            .withValueType(ValueType.AGENT_HISTORY)
            .withIntent(AgentHistoryIntent.CREATED)
            .filter(
                r ->
                    ((AgentHistoryRecordValue) r.getValue())
                        .getHistoryItemId()
                        .equals("item-discarded"))
            .toList();
    assertThat(createdForDiscardedItem)
        .as(
            "two CREATED events exist for item-discarded in total: one from the first push, one"
                + " from this resend")
        .hasSize(2);

    // then — the metrics half: "item-discarded"'s earlier copy was discarded, but its metrics
    // were already accumulated when it was first created, so this resend must not pay for them a
    // second time — the totals stay exactly where they were before the resend, and the resend's
    // own changedAttributes omits "metrics" as the observable signal that it was skipped.
    assertThat(resend.getValue().getMetrics().getInputTokens())
        .as("item-discarded's metrics were already accumulated on its first creation")
        .isEqualTo(10L + 7L);
    assertThat(resend.getValue().getMetrics().getOutputTokens())
        .as("item-discarded's metrics were already accumulated on its first creation")
        .isEqualTo(5L + 3L);
    assertThat(resend.getValue().getChangedAttributes())
        .as("metrics were skipped for both the duplicate and the resent-but-discarded item")
        .doesNotContain("metrics");
  }

  @Test
  public void shouldRejectResendAfterJobHasCompleted() {
    // given — a sequential multi-instance AI-agent service task. A plain single-service-task
    // process won't do here: completing EI1's job also completes EI1, and with a single task that
    // completes the whole process (and the agent instance with it), leaving nothing left to
    // reject a resend against. Routing the resend through EI2 — still active — isolates the one
    // thing this test is about: job1 itself is no longer ACTIVATED.
    final var multiInstanceProcessId = "dedup-sequential-multi-instance-resend-after-completion";
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

    // when — a resend reuses job1 (EI1's own, now-completed job) but targets EI2 (still active),
    // so the rejection reflects job1's own state, not EI2's — which is otherwise valid.
    final var job1Key =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withType(helper.getJobType())
            .filter(r -> r.getValue().getElementInstanceKey() == ei1)
            .getFirst()
            .getKey();

    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2)
            .withJobKey(job1Key)
            .withHistory(
                List.of(
                    new AgentHistoryRecord()
                        .setHistoryItemId("item-after-completion")
                        .setRole(AgentHistoryRole.USER)
                        .setLoopIteration(1)
                        .addContent(
                            new AgentHistoryMessageContent()
                                .setContentType(AgentHistoryContentType.TEXT)
                                .setText("resend after completion"))))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType())
        .as("job1 is no longer ACTIVATED, so the resend must be rejected")
        .isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType())
        .as("job1 is no longer ACTIVATED, so the resend must be rejected")
        .isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .as("job1 is no longer ACTIVATED, so the resend must be rejected")
        .contains(
            ("Expected to update agent instance related to job with key '%d', but job was not "
                    + "active.")
                .formatted(job1Key));
  }
}
