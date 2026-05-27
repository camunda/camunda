/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.agentinstance;

import static io.camunda.zeebe.engine.util.client.AgentInstanceClient.tool;
import static io.camunda.zeebe.engine.util.client.AgentInstanceClient.tools;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentInstanceUpdateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldEmitUpdatedEventForValidUpdateCommand() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .update();

    // then
    assertThat(updated.getKey()).isEqualTo(agentInstanceKey);
    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(updated.getValue().getChangedAttributes()).containsExactly("status");
  }

  @Test
  public void shouldOnlyUpdateAttributesNamedInChangedAttributes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — the command attempts to set status, metrics, and tools, but only "status" is
    // listed in changedAttributes via the escape hatch — the other fields must be ignored.
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .withMetricsDelta(99L, 88L, 7, 5)
            .withTools(tools(tool("calc", "Calculator", "calc-task")))
            .withChangedAttributes(List.of("status"))
            .update();

    // then — only status is applied; metrics stay zero, tools stay empty.
    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(updated.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(updated.getValue().getMetrics().getModelCalls()).isZero();
    assertThat(updated.getValue().getMetrics().getToolCalls()).isZero();
    assertThat(updated.getValue().getTools()).isEmpty();
    assertThat(updated.getValue().getChangedAttributes()).containsExactly("status");
  }

  @Test
  public void shouldEmitUpdatedEventCarryingChangedAttributes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — both status and metrics are updated.
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .withMetricsDelta(1L, 1L, 1, 1)
            .withChangedAttributes(List.of("status", "metrics"))
            .update();

    // then — UPDATED carries the effective changedAttributes (both "status" and "metrics").
    assertThat(updated.getValue().getChangedAttributes())
        .containsExactlyInAnyOrder("status", "metrics");
  }

  @Test
  public void shouldRejectUpdateWithEmptyChangedAttributes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — valid elementInstanceKey supplied, but changedAttributes is empty
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withChangedAttributes(List.of())
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("changedAttributes");
  }

  @Test
  public void shouldDedupeDuplicateAttributesInChangedAttributes() {
    // given — a duplicated "metrics" entry in changedAttributes. Without dedup the delta would
    // be applied twice; the processor iterates the validated set so each attribute is patched once.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(10L, 5L, 1, 2)
            .withChangedAttributes(List.of("metrics", "metrics"))
            .update();

    // then — delta applied once
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(10L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(5L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(2);
    assertThat(updated.getValue().getChangedAttributes()).containsExactly("metrics");
  }

  @Test
  public void shouldRejectUnknownAttributeInChangedAttributes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    for (final var unknownAttr : List.of("foo", "elementInstanceKey")) {
      // when
      final Record<?> rejection =
          ENGINE
              .agentInstances()
              .withAgentInstanceKey(agentInstanceKey)
              .withElementInstanceKey(serviceTaskInstance.getKey())
              .withChangedAttributes(List.of(unknownAttr))
              .expectRejection()
              .update();

      // then
      assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
      assertThat(rejection.getRejectionReason()).contains(unknownAttr);
    }
  }

  @Test
  public void shouldRejectAttemptToUpdateLimits() {
    // given — limits is a legitimate field on CREATE but is intentionally not in the
    // ALLOWED_ATTRIBUTES set for UPDATE, so naming it must be rejected as unknown.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withChangedAttributes(List.of("limits"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("limits");
  }

  @Test
  public void shouldRejectWhenAgentInstanceNotFound() {
    // given
    final var nonExistentKey = 987654321L;

    // when
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(nonExistentKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(nonExistentKey));
  }

  @Test
  public void shouldAccumulateMetricsAsDeltas() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .withMetricsDelta(10L, 5L, 1, 0)
        .withChangedAttributes(List.of("metrics"))
        .update();

    final var secondUpdate =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(20L, 15L, 2, 1)
            .withChangedAttributes(List.of("metrics"))
            .update();

    // then
    final var updates =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .limit(2)
            .toList();
    assertThat(updates).hasSize(2);

    assertThat(secondUpdate.getValue().getMetrics().getInputTokens()).isEqualTo(30L);
    assertThat(secondUpdate.getValue().getMetrics().getOutputTokens()).isEqualTo(20L);
    assertThat(secondUpdate.getValue().getMetrics().getModelCalls()).isEqualTo(3);
    assertThat(secondUpdate.getValue().getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(secondUpdate.getValue().getChangedAttributes()).containsExactly("metrics");
  }

  @Test
  public void shouldRejectMetricDeltaBelowNotProvidedSentinel() {
    // given — anything below -1 (the not-provided sentinel) is invalid
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(-2L, 0L, 0, 0)
            .withChangedAttributes(List.of("metrics"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).containsIgnoringCase("metric delta");
  }

  @Test
  public void shouldTreatMetricDeltaOfMinusOneAsNotProvided() {
    // given — bring metrics to a known baseline
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .withMetricsDelta(10L, 20L, 1, 2)
        .withChangedAttributes(List.of("metrics"))
        .update();

    // when — only inputTokens is provided; the other fields are -1 (not provided)
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(5L, -1L, -1, -1)
            .withChangedAttributes(List.of("metrics"))
            .update();

    // then — only inputTokens moves; not-provided fields are left untouched, and "metrics" still
    // appears in changedAttributes because at least one field changed
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(15L);
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isEqualTo(20L);
    assertThat(updated.getValue().getMetrics().getModelCalls()).isEqualTo(1);
    assertThat(updated.getValue().getMetrics().getToolCalls()).isEqualTo(2);
    assertThat(updated.getValue().getChangedAttributes()).contains("metrics");
  }

  @Test
  public void shouldDropMetricsFromChangedAttributesWhenAllDeltasAreNoOp() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — all deltas are either 0 (provided but no change) or -1 (not provided)
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(0L, -1L, 0, -1)
            .withChangedAttributes(List.of("metrics"))
            .update();

    // then — UPDATED is still emitted, but the event signals that no field changed by omitting
    // "metrics" from changedAttributes
    assertThat(updated.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(updated.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(updated.getValue().getMetrics().getModelCalls()).isZero();
    assertThat(updated.getValue().getMetrics().getToolCalls()).isZero();
    assertThat(updated.getValue().getChangedAttributes()).doesNotContain("metrics");
  }

  @Test
  public void shouldNotEnforceLimits() {
    // given — CREATE the agent instance directly (limits are reset on CREATE anyway, so the
    // intent here is to assert the UPDATE processor does not enforce maxTokens / etc.).
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — UPDATE with input-tokens delta of 200, well above any reasonable max-tokens.
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(200L, 0L, 0, 0)
            .withChangedAttributes(List.of("metrics"))
            .update();

    // then — UPDATED is emitted normally; no limit-based rejection.
    assertThat(updated.getIntent()).isEqualTo(AgentInstanceIntent.UPDATED);
    assertThat(updated.getValue().getMetrics().getInputTokens()).isEqualTo(200L);
  }

  @Test
  public void shouldReplaceToolsListEntirely() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();
    final var firstTools = tools(tool("t1", "first tool", "t1-elem"));
    final var secondTools = tools(tool("t2", "second tool", "t2-elem"));

    // when
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .withTools(firstTools)
        .withChangedAttributes(List.of("tools"))
        .update();
    final var second =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withTools(secondTools)
            .withChangedAttributes(List.of("tools"))
            .update();

    // then — final tools list contains only the second tool; the first is gone.
    assertThat(second.getValue().getTools())
        .singleElement()
        .satisfies(t -> assertThat(t.getName()).isEqualTo("t2"));
  }

  @Test
  public void shouldClearToolsListWhenEmptyListProvided() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — first add a tool, then clear with an empty list.
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .withTools(tools(tool("t1", "first tool", "t1-elem")))
        .withChangedAttributes(List.of("tools"))
        .update();
    final var cleared =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withTools(List.of())
            .withChangedAttributes(List.of("tools"))
            .update();

    // then
    assertThat(cleared.getValue().getTools()).isEmpty();
  }

  @Test
  public void shouldDropStatusFromChangedAttributesOnNoOpStatusUpdate() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — set status to the same value as current (INITIALIZING)
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.INITIALIZING)
            .withChangedAttributes(List.of("status"))
            .update();

    // then — UPDATED is still emitted but without "status" in changedAttributes (no-op)
    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
    assertThat(updated.getValue().getChangedAttributes()).isEmpty();
  }

  @Test
  public void shouldRejectStatusUnspecifiedWhenStatusInChangedAttributes() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — explicitly UNSPECIFIED with status named in changedAttributes; this falls into the
    // transition matrix (UNSPECIFIED is not an active target state) and is rejected as such.
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.UNSPECIFIED)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then - the transition-matrix rejection cites both from and to so that a future change which
    // accidentally accepts UNSPECIFIED (e.g. by adding it to ACTIVE_STATUSES) would not pass with a
    // RejectionType-only assertion.
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(AgentInstanceStatus.INITIALIZING.name())
        .contains(AgentInstanceStatus.UNSPECIFIED.name())
        .contains("transition is not allowed");
  }

  @Test
  public void shouldRejectTransitionToInitializingFromToolDiscovery() {
    assertRejectsTransitionToInitializingFrom(AgentInstanceStatus.TOOL_DISCOVERY);
  }

  @Test
  public void shouldRejectTransitionToInitializingFromThinking() {
    assertRejectsTransitionToInitializingFrom(AgentInstanceStatus.THINKING);
  }

  @Test
  public void shouldRejectTransitionToInitializingFromToolCalling() {
    assertRejectsTransitionToInitializingFrom(AgentInstanceStatus.TOOL_CALLING);
  }

  @Test
  public void shouldRejectTransitionToInitializingFromIdle() {
    assertRejectsTransitionToInitializingFrom(AgentInstanceStatus.IDLE);
  }

  @Test
  public void shouldRejectUpdateSettingStatusToCompleted() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.COMPLETED)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldAllowStatusTransitionsBetweenActiveStates() {
    // given — verify the matrix of active-state -> active-state transitions, including same-state.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var activeStates =
        List.of(
            AgentInstanceStatus.INITIALIZING,
            AgentInstanceStatus.TOOL_DISCOVERY,
            AgentInstanceStatus.THINKING,
            AgentInstanceStatus.TOOL_CALLING,
            AgentInstanceStatus.IDLE);

    for (final var from : activeStates) {
      for (final var to : activeStates) {
        // Skip transitions to INITIALIZING from non-INITIALIZING states — those are rejected.
        if (to == AgentInstanceStatus.INITIALIZING && from != AgentInstanceStatus.INITIALIZING) {
          continue;
        }
        // Spin up a fresh process instance + agent instance per matrix cell so each transition
        // starts from a known baseline.
        final var processInstanceKey =
            ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
        final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
        final var agentInstanceKey =
            ENGINE
                .agentInstances()
                .withElementInstanceKey(serviceTaskInstance.getKey())
                .create()
                .getValue()
                .getAgentInstanceKey();
        // Move the agent into the "from" state if necessary.
        if (from != AgentInstanceStatus.INITIALIZING) {
          ENGINE
              .agentInstances()
              .withAgentInstanceKey(agentInstanceKey)
              .withElementInstanceKey(serviceTaskInstance.getKey())
              .withStatus(from)
              .withChangedAttributes(List.of("status"))
              .update();
        }
        // when
        final var updated =
            ENGINE
                .agentInstances()
                .withAgentInstanceKey(agentInstanceKey)
                .withElementInstanceKey(serviceTaskInstance.getKey())
                .withStatus(to)
                .withChangedAttributes(List.of("status"))
                .update();

        // then
        assertThat(updated.getIntent())
            .as("expected transition from %s to %s to be allowed", from, to)
            .isEqualTo(AgentInstanceIntent.UPDATED);
        assertThat(updated.getValue().getStatus()).isEqualTo(to);
      }
    }
  }

  @Test
  public void shouldAssociateElementInstanceOnUpdate() {
    // given — a multi-instance service task produces EI₁ and EI₂ simultaneously.
    final var multiInstanceProcessId = "multi-instance-assoc";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(multiInstanceProcessId)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t ->
                        t.zeebeJobType("agent")
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
    final var ei1 = children.get(0);
    final var ei2 = children.get(1);

    // Create agent instance on EI₁.
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — UPDATE supplying EI₂ (new association)
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .update();

    // then — plural list carries [EI₁, EI₂], scalar = EI₂
    assertThat(updated.getValue().getElementInstanceKeys())
        .containsExactly(ei1.getKey(), ei2.getKey());
    assertThat(updated.getValue().getElementInstanceKey()).isEqualTo(ei2.getKey());
  }

  @Test
  public void shouldUpdateScalarOnReEntryToAlreadyTrackedElementInstance() {
    // given — multi-instance produces EI₁ and EI₂. Agent created on EI₁, then re-associated to EI₂.
    // The plural list ends up as [EI₁, EI₂] with scalar = EI₂.
    final var multiInstanceProcessId = "scalar-reentry";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(multiInstanceProcessId)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t ->
                        t.zeebeJobType("agent")
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
    final var ei1 = children.get(0);
    final var ei2 = children.get(1);

    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(ei2.getKey())
        .withStatus(AgentInstanceStatus.THINKING)
        .update();

    // when — UPDATE re-supplies EI₁ (already in the plural list) alongside a status change
    final var updated =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei1.getKey())
            .withStatus(AgentInstanceStatus.IDLE)
            .update();

    // then — list is unchanged (no duplicates), but scalar moves back to the supplied key
    // because it represents the currently-active element instance.
    assertThat(updated.getValue().getElementInstanceKeys())
        .containsExactly(ei1.getKey(), ei2.getKey());
    assertThat(updated.getValue().getElementInstanceKey()).isEqualTo(ei1.getKey());
  }

  @Test
  public void shouldRejectUpdateWhenElementInstanceKeyMissing() {
    // given — agent instance created on a service task.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — UPDATE without elementInstanceKey (default scalar = -1 sentinel)
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            // intentionally no withElementInstanceKey() call — default is -1 sentinel
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then — engine rejects the command (defense-in-depth against internal command bus misuse)
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("elementInstanceKey");
  }

  @Test
  public void shouldRejectUpdateWhenAssociatedElementInstanceNotFound() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    final var nonExistentEiKey = 987654321L;

    // when
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(nonExistentEiKey)
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(nonExistentEiKey));
  }

  @Test
  public void shouldRejectUpdateWhenAssociatedElementInstanceNotActive() {
    // given — a service task with a faulty output expression. Completing the job triggers output
    // mapping evaluation, which fails and raises an incident. The element instance is left in
    // ELEMENT_COMPLETING state (not active) and is not removed from state.
    final var notActiveProcessId = "not-active-process";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(notActiveProcessId)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType("agent").zeebeOutputExpression("assert(x, x != null)", "y"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(notActiveProcessId).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // Complete the job — output mapping fails, raises incident, EI is stuck in COMPLETING.
    ENGINE.job().ofInstance(processInstanceKey).withType("agent").complete();
    RecordingExporter.incidentRecords().withProcessInstanceKey(processInstanceKey).getFirst();

    // when — attempt to UPDATE referencing the now-inactive (COMPLETING) element instance
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(String.valueOf(serviceTaskInstance.getKey()));
  }

  @Test
  public void shouldRejectUpdateWhenAssociatedElementInstanceElementIdMismatch() {
    // given — agent instance on SERVICE_TASK_ID; attempt to UPDATE with an EI from a DIFFERENT
    // task. A parallel gateway activates both tasks simultaneously.
    final var mismatchProcessId = "mismatch-element-id";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(mismatchProcessId)
                .startEvent()
                .parallelGateway("split")
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .parallelGateway("merge")
                .endEvent()
                .moveToNode("split")
                .serviceTask("other-task", t -> t.zeebeJobType("other"))
                .connectTo("merge")
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(mismatchProcessId).create();
    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final var otherTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("other-task")
            .getFirst();

    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — UPDATE with EI from "other-task" (different elementId)
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(otherTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains(SERVICE_TASK_ID).contains("other-task");
  }

  @Test
  public void shouldRejectUpdateWhenAssociatedElementInstanceProcessInstanceKeyMismatch() {
    // given — two separate process instances; agent instance on PI₁'s task,
    // UPDATE supplies EI from PI₂.
    final var piMismatchProcessId = "pi-mismatch";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(piMismatchProcessId)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var pi1Key = ENGINE.processInstance().ofBpmnProcessId(piMismatchProcessId).create();
    final var pi2Key = ENGINE.processInstance().ofBpmnProcessId(piMismatchProcessId).create();
    final var ei1 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(pi1Key)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();
    final var ei2 =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(pi2Key)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();

    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — UPDATE with EI from PI₂ (different processInstanceKey)
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(ei2.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains(String.valueOf(pi1Key))
        .contains(String.valueOf(pi2Key));
  }

  @Test
  public void
      shouldRejectUpdateWhenAssociatedElementInstanceAlreadyAssociatedWithDifferentAgentInstance() {
    // given — two multi-instance children; EI₁ → agentInstance₁, EI₂ → agentInstance₂.
    // Attempt to UPDATE agentInstance₁ by supplying EI₂ (already associated with agentInstance₂).
    final var alreadyAssocProcessId = "already-assoc";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(alreadyAssocProcessId)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t ->
                        t.zeebeJobType("agent")
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
            .ofBpmnProcessId(alreadyAssocProcessId)
            .withVariables(Map.of("items", List.of("a", "b")))
            .create();
    final var children =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList();
    final var ei1Key = children.get(0).getKey();
    final var ei2Key = children.get(1).getKey();

    final var agentInstance1Key =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei1Key)
            .create()
            .getValue()
            .getAgentInstanceKey();
    final var agentInstance2Key =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(ei2Key)
            .create()
            .getValue()
            .getAgentInstanceKey();

    // when — try to associate EI₂ (already owned by agentInstance₂) with agentInstance₁
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstance1Key)
            .withElementInstanceKey(ei2Key)
            .withStatus(AgentInstanceStatus.THINKING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
    assertThat(rejection.getRejectionReason())
        .contains(String.valueOf(ei2Key))
        .contains(String.valueOf(agentInstance2Key));
  }

  private void assertRejectsTransitionToInitializingFrom(final AgentInstanceStatus from) {
    // given — move the agent from INITIALIZING into the requested "from" state.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);
    final var agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .create()
            .getValue()
            .getAgentInstanceKey();
    ENGINE
        .agentInstances()
        .withAgentInstanceKey(agentInstanceKey)
        .withElementInstanceKey(serviceTaskInstance.getKey())
        .withStatus(from)
        .withChangedAttributes(List.of("status"))
        .update();

    // when — attempt to transition back to INITIALIZING.
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.INITIALIZING)
            .withChangedAttributes(List.of("status"))
            .expectRejection()
            .update();

    // then
    assertThat(rejection.getRejectionType())
        .as("transition from %s -> INITIALIZING should be rejected", from)
        .isEqualTo(RejectionType.INVALID_STATE);
  }

  private static Record<ProcessInstanceRecordValue> awaitServiceTaskActivated(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withElementId(SERVICE_TASK_ID)
        .getFirst();
  }
}
