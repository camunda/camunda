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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AgentInstanceUpdateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "service-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  /**
   * Deploys a process, creates a process instance, awaits the service-task activation, sends a
   * CREATE agent instance command and returns the agentInstanceKey from the resulting CREATED
   * event.
   */
  private long createAgentInstance() {
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

    final var serviceTaskInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId(SERVICE_TASK_ID)
            .getFirst();

    final var createCommand =
        new AgentInstanceRecord().setElementInstanceKey(serviceTaskInstance.getKey());
    ENGINE.writeRecords(
        RecordToWrite.command().agentInstance(AgentInstanceIntent.CREATE, createCommand));

    return RecordingExporter.agentInstanceRecords(AgentInstanceIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getValue()
        .getAgentInstanceKey();
  }

  @Test
  public void shouldEmitUpdatedEventForValidUpdateCommand() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.THINKING)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final var updated =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .getFirst();

    assertThat(updated.getKey()).isEqualTo(agentInstanceKey);
    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(updated.getValue().getChangedAttributes()).containsExactly("status");
  }

  @Test
  public void shouldAccumulateMetricsAsDeltas() {
    // given
    final var agentInstanceKey = createAgentInstance();

    final var firstDeltas = new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey);
    firstDeltas.getMetrics().setInputTokens(10).setOutputTokens(5).setModelCalls(1).setToolCalls(0);
    firstDeltas.setChangedAttributes(List.of("metrics"));

    final var secondDeltas = new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey);
    secondDeltas
        .getMetrics()
        .setInputTokens(20)
        .setOutputTokens(15)
        .setModelCalls(2)
        .setToolCalls(1);
    secondDeltas.setChangedAttributes(List.of("metrics"));

    // when
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, firstDeltas));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, secondDeltas));

    // then
    final var updates =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .limit(2)
            .toList();

    assertThat(updates).hasSize(2);
    final var secondUpdated = updates.get(1).getValue();
    assertThat(secondUpdated.getMetrics().getInputTokens()).isEqualTo(30L);
    assertThat(secondUpdated.getMetrics().getOutputTokens()).isEqualTo(20L);
    assertThat(secondUpdated.getMetrics().getModelCalls()).isEqualTo(3);
    assertThat(secondUpdated.getMetrics().getToolCalls()).isEqualTo(1);
    assertThat(secondUpdated.getChangedAttributes()).containsExactly("metrics");
  }

  @Test
  public void shouldDropStatusFromChangedAttributesOnNoOpStatusUpdate() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when — set status to the same value as current (INITIALIZING)
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final var updated =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
            .withAgentInstanceKey(agentInstanceKey)
            .getFirst();

    assertThat(updated.getValue().getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING);
    assertThat(updated.getValue().getChangedAttributes()).isEmpty();
  }

  @Test
  public void shouldRejectWhenAgentInstanceNotFound() {
    // given
    final var nonExistentKey = 987654321L;

    // when
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(nonExistentKey)
            .setStatus(AgentInstanceStatus.THINKING)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(nonExistentKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejection.getRejectionReason()).contains(String.valueOf(nonExistentKey));
  }

  @Test
  public void shouldRejectEmptyChangedAttributes() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setChangedAttributes(List.of());
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("changedAttributes");
  }

  @Test
  public void shouldRejectUnknownAttributeInChangedAttributes() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setChangedAttributes(List.of("foo"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).contains("foo");
  }

  @Test
  public void shouldRejectNegativeMetricDelta() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when
    final var updateCommand = new AgentInstanceRecord().setAgentInstanceKey(agentInstanceKey);
    updateCommand.getMetrics().setInputTokens(-1L);
    updateCommand.setChangedAttributes(List.of("metrics"));

    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason()).containsIgnoringCase("negative");
  }

  @Test
  public void shouldRejectStatusUnspecifiedWhenStatusInChangedAttributes() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when — explicitly UNSPECIFIED with status named in changedAttributes
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.UNSPECIFIED)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectTransitionToInitializing() {
    // given — first transition from INITIALIZING to THINKING
    final var agentInstanceKey = createAgentInstance();
    final var firstUpdate =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.THINKING)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, firstUpdate));
    RecordingExporter.agentInstanceRecords(AgentInstanceIntent.UPDATED)
        .withAgentInstanceKey(agentInstanceKey)
        .getFirst();

    // when — attempt to go back to INITIALIZING
    final var backToInitializing =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.INITIALIZING)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, backToInitializing));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldRejectUpdateSettingStatusToCompleted() {
    // given
    final var agentInstanceKey = createAgentInstance();

    // when
    final var updateCommand =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setStatus(AgentInstanceStatus.COMPLETED)
            .setChangedAttributes(List.of("status"));
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, updateCommand));

    // then
    final Record<?> rejection =
        RecordingExporter.agentInstanceRecords()
            .onlyCommandRejections()
            .withIntent(AgentInstanceIntent.UPDATE)
            .getFirst();

    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }
}
