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
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceTool;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
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

public class AgentInstanceCreateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";
  private static final String AD_HOC_SUB_PROCESS_ID = "ad-hoc-subprocess";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRoundtripSettableFieldsAndResetEngineManagedOnes() {
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

    // when -- the command sets every field, including engine-managed ones (status, metrics,
    // tools) that the client must not be able to control on CREATE.
    final var seededTool =
        new AgentInstanceTool()
            .setName("seeded-tool")
            .setDescription("a tool seeded by the client")
            .setElementId("inner-task");
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withDefinition("gpt-4o", "openai", "You are a helpful agent.")
            .withLimits(1000L, 10, 20)
            .withStatus(AgentInstanceStatus.COMPLETED)
            .withMetricsDelta(50L, 25L, 5, 3)
            .withTools(List.of(seededTool))
            .create();

    // then -- settable fields (definition, limits) round-trip into CREATED.
    assertThat(created.getValue().getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(created.getValue().getDefinition().getProvider()).isEqualTo("openai");
    assertThat(created.getValue().getDefinition().getSystemPrompt())
        .isEqualTo("You are a helpful agent.");
    assertThat(created.getValue().getLimits().getMaxTokens()).isEqualTo(1000L);
    assertThat(created.getValue().getLimits().getMaxModelCalls()).isEqualTo(10);
    assertThat(created.getValue().getLimits().getMaxToolCalls()).isEqualTo(20);

    // then -- engine-managed fields are reset regardless of what the command supplied.
    assertThat(created.getValue().getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
    assertThat(created.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getModelCalls()).isZero();
    assertThat(created.getValue().getMetrics().getToolCalls()).isZero();
    assertThat(created.getValue().getTools()).isEmpty();
  }

  @Test
  public void shouldMaterializeIdentityFieldsFromElementInstance() {
    // given
    final var customElementId = "my-agent";
    final var processMetadata =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .serviceTask(customElementId, t -> t.zeebeJobType("agent"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var elementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(customElementId)
            .getFirst();

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(elementInstance.getKey()).create();

    // then
    assertThat(created.getValue().getElementInstanceKey()).isEqualTo(elementInstance.getKey());
    assertThat(created.getValue().getElementId()).isEqualTo(customElementId);
    assertThat(created.getValue().getBpmnProcessId()).isEqualTo(PROCESS_ID);
    assertThat(created.getValue().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(created.getValue().getProcessDefinitionKey())
        .isEqualTo(processMetadata.getProcessDefinitionKey());
    assertThat(created.getValue().getProcessDefinitionVersion())
        .isEqualTo(processMetadata.getVersion());
    assertThat(created.getValue().getTenantId())
        .isEqualTo(elementInstance.getValue().getTenantId());
  }

  @Test
  public void shouldFetchVersionTagFromProcessState() {
    // given
    final var versionTag = "v1.2.3";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .versionTag(versionTag)
                .startEvent()
                .serviceTask(SERVICE_TASK_ID, t -> t.zeebeJobType("agent"))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(serviceTaskInstance.getKey()).create();

    // then
    assertThat(created.getValue().getVersionTag()).isEqualTo(versionTag);
  }

  @Test
  public void shouldMaterializeStatusInitializingOnCreate() {
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

    // when -- the command tries to set a different status; engine must ignore it.
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withStatus(AgentInstanceStatus.THINKING)
            .create();

    // then
    assertThat(created.getValue().getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
  }

  @Test
  public void shouldDefaultMetricsToZeroAndToolsEmpty() {
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

    // when -- even when the command carries non-default metrics, the engine resets them.
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withMetricsDelta(50L, 25L, 5, 3)
            .create();

    // then
    assertThat(created.getValue().getMetrics().getInputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getOutputTokens()).isZero();
    assertThat(created.getValue().getMetrics().getModelCalls()).isZero();
    assertThat(created.getValue().getMetrics().getToolCalls()).isZero();
    assertThat(created.getValue().getTools()).isEmpty();
  }

  @Test
  public void shouldPreserveLimitsFromCommand() {
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

    // when -- limits supplied on the command must be preserved on the CREATED event.
    final var created =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .withLimits(1000L, 10, 20)
            .create();

    // then
    assertThat(created.getValue().getLimits().getMaxTokens()).isEqualTo(1000L);
    assertThat(created.getValue().getLimits().getMaxModelCalls()).isEqualTo(10);
    assertThat(created.getValue().getLimits().getMaxToolCalls()).isEqualTo(20);
  }

  @Test
  public void shouldAcceptServiceTaskElementType() {
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

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(serviceTaskInstance.getKey()).create();

    // then
    assertThat(created.getIntent()).isEqualTo(AgentInstanceIntent.CREATED);
    assertThat(created.getRecordType()).isEqualTo(RecordType.EVENT);
  }

  @Test
  public void shouldAcceptAdHocSubProcessElementType() {
    // given -- an ad-hoc subprocess with an inner task and a completion condition that keeps the
    // ad-hoc subprocess element active long enough for us to attach an agent instance to it.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .adHocSubProcess(
                    AD_HOC_SUB_PROCESS_ID,
                    asp -> {
                      asp.task("inner-task");
                      asp.completionCondition("=completionCondition");
                    })
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("completionCondition", false))
            .create();

    final var adHocInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst();

    // when
    final var created =
        ENGINE.agentInstances().withElementInstanceKey(adHocInstance.getKey()).create();

    // then
    assertThat(created.getIntent()).isEqualTo(AgentInstanceIntent.CREATED);
    assertThat(created.getValue().getElementId()).isEqualTo(AD_HOC_SUB_PROCESS_ID);
    assertThat(created.getValue().getElementInstanceKeys()).containsExactly(adHocInstance.getKey());
  }

  @Test
  public void shouldCreateForEachMultiInstanceChildElementInstance() {
    // given -- a multi-instance service task with two collection items.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
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
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("items", List.of("a", "b")))
            .create();

    final var children =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .limit(2)
            .toList();
    assertThat(children).hasSize(2);

    // when -- create an agent instance for each child element instance.
    final var firstAgent =
        ENGINE.agentInstances().withElementInstanceKey(children.get(0).getKey()).create();
    final var secondAgent =
        ENGINE.agentInstances().withElementInstanceKey(children.get(1).getKey()).create();

    // then -- each child gets its own agent instance with distinct keys.
    assertThat(firstAgent.getValue().getElementInstanceKey()).isEqualTo(children.get(0).getKey());
    assertThat(secondAgent.getValue().getElementInstanceKey()).isEqualTo(children.get(1).getKey());
    assertThat(secondAgent.getValue().getAgentInstanceKey())
        .isNotEqualTo(firstAgent.getValue().getAgentInstanceKey());
  }

  @Test
  public void shouldTreatSecondCreateForSameElementInstanceAsIdempotentSuccess() {
    // given -- only one agent instance can exist per element instance; the public API has no 409.
    // The engine short-circuits a second CREATE: a rejection lands on the stream (suppressing a
    // second CREATED event), and the client response carries the existing record.
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

    // when
    final var first =
        ENGINE.agentInstances().withElementInstanceKey(serviceTaskInstance.getKey()).create();
    final var secondRejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .expectRejection()
            .create();

    // then -- the second CREATE is rejected on the stream with ALREADY_EXISTS.
    assertThat(secondRejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(secondRejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
    assertThat(secondRejection.getRejectionReason())
        .contains(String.valueOf(first.getValue().getAgentInstanceKey()));
  }

  @Test
  public void shouldRejectWhenElementInstanceNotFound() {
    // given
    final var nonExistingElementInstanceKey = 123456789L;

    // when
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(nonExistingElementInstanceKey)
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason())
        .contains(String.valueOf(nonExistingElementInstanceKey));
  }

  @Test
  public void shouldRejectWhenElementInstanceIsNotActive() {
    // given -- a service task with a faulty output expression. Completing the job triggers
    // output mapping evaluation, which fails and raises an incident. The element instance is
    // left in state COMPLETING (not active) and is not removed from state.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType("agent").zeebeOutputExpression("assert(x, x != null)", "y"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    ENGINE.job().ofInstance(processInstanceKey).withType("agent").complete();
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when -- the element instance still exists but is no longer active.
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains(String.valueOf(serviceTaskInstance.getKey()));
  }

  @Test
  public void shouldReturnExistingRecordForRetryAfterElementInstanceLeftActive() {
    // given -- an element instance that successfully got an agent created, then transitioned out
    // of ACTIVE (parked in COMPLETING behind an incident from a faulty output expression). The
    // element instance still exists in state and carries the back-link to the agent instance.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    SERVICE_TASK_ID,
                    t -> t.zeebeJobType("agent").zeebeOutputExpression("assert(x, x != null)", "y"))
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var serviceTaskInstance = awaitServiceTaskActivated(processInstanceKey);

    final var first =
        ENGINE.agentInstances().withElementInstanceKey(serviceTaskInstance.getKey()).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("agent").complete();
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when -- a retry CREATE arrives after the element instance has left ACTIVE.
    final var retryRejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(serviceTaskInstance.getKey())
            .expectRejection()
            .create();

    // then -- the retry is short-circuited as an idempotent success against the existing record,
    // not rejected as INVALID_STATE. The stream still carries ALREADY_EXISTS to suppress a
    // duplicate CREATED event.
    assertThat(retryRejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(retryRejection.getRejectionType()).isEqualTo(RejectionType.ALREADY_EXISTS);
    assertThat(retryRejection.getRejectionReason())
        .contains(String.valueOf(first.getValue().getAgentInstanceKey()));
  }

  @Test
  public void shouldRejectWhenElementTypeIsUserTask() {
    // given -- a process with a USER_TASK element that stays active until a user acts on it.
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("user-task", t -> t.zeebeUserTask())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var userTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.USER_TASK)
            .getFirst();

    // when
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(userTaskInstance.getKey())
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("USER_TASK");
  }

  @Test
  public void shouldRejectWhenElementTypeIsProcessRoot() {
    // given -- the PROCESS root element instance is active by definition during a running PI,
    // but it isn't a supported type for agent instances.
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
    awaitServiceTaskActivated(processInstanceKey);

    // when -- use the process instance key (the root PROCESS element instance) as the target.
    final Record<?> rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(processInstanceKey)
            .expectRejection()
            .create();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("PROCESS");
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
