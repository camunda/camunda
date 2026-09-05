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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the suspension gate classifications of AgentInstance processors.
 *
 * <p>CREATE and UPDATE are classified {@code isInternalCommand() ? BUFFER : REJECT}:
 *
 * <ul>
 *   <li>External commands (from REST/gRPC clients, carrying request metadata) are REJECT'd to
 *       prevent an authorization bypass: a buffered command is drained as an internal command on
 *       resume, skipping the CSL check.
 *   <li>Internal commands (engine-generated follow-ups) are BUFFER'd so they can be replayed when
 *       the instance resumes.
 * </ul>
 *
 * <p>COMPLETE is classified {@code PROCESS} so that teardown bookkeeping is not orphaned when a
 * suspended instance is cancelled.
 *
 * <p>{@link io.camunda.zeebe.engine.processing.streamprocessor.SuspensionCheck} resolves the
 * process instance key for CREATE by looking up the target element instance ({@code
 * elementInstanceKey}), and for UPDATE by looking up the agent instance identified by the command
 * key — both are populated by real clients, so the gate fires for genuine external/internal
 * commands without any test scaffolding.
 */
public class AgentInstanceSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectExternalCreateCommandWhileSuspended() {
    // given
    final long elementInstanceKey = activateServiceTask();
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withRecordKey(elementInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — create(username) carries request metadata, so isInternalCommand() is false and the
    // gate classifies it as REJECT
    final var rejection =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .expectRejection()
            .create("some-user");

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldRejectExternalUpdateCommandWhileSuspended() {
    // given
    final long elementInstanceKey = activateServiceTask();
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withRecordKey(elementInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "sys")
            .withLimits(100L, 5, 5)
            .create()
            .getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — update(username) carries request metadata, so the gate classifies it as REJECT
    final var rejection =
        ENGINE
            .agentInstances()
            .withAgentInstanceKey(agentInstanceKey)
            .withMetricsDelta(10L, 5L, 1, 1)
            .expectRejection()
            .update("some-user");

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }

  @Test
  public void shouldBufferInternalCreateCommandWhileSuspended() {
    // given
    final long elementInstanceKey = activateServiceTask();
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withRecordKey(elementInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — RecordToWrite.command() produces an internal command (no request metadata), so the
    // gate classifies it as BUFFER
    final var commandRecord = new AgentInstanceRecord().setElementInstanceKey(elementInstanceKey);
    ENGINE.writeRecords(
        RecordToWrite.command().agentInstance(AgentInstanceIntent.CREATE, commandRecord));

    // then — the gate queues the internal command; it will be drained when the instance resumes
    final Record<RecordValue> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var v = (BufferedCommandRecordValue) r.getValue();
                  return v.getProcessInstanceKey() == processInstanceKey
                      && v.getValueType() == ValueType.AGENT_INSTANCE;
                })
            .getFirst();
    assertThat(((BufferedCommandRecordValue) buffered.getValue()).getIntent())
        .isEqualTo(AgentInstanceIntent.CREATE);
  }

  @Test
  public void shouldBufferInternalUpdateCommandWhileSuspended() {
    // given
    final long elementInstanceKey = activateServiceTask();
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withRecordKey(elementInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "sys")
            .withLimits(100L, 5, 5)
            .create()
            .getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — RecordToWrite.command() produces an internal command, so the gate classifies it as
    // BUFFER
    final var commandRecord = new AgentInstanceRecord().setChangedAttributes(List.of("metrics"));
    commandRecord
        .getMetrics()
        .setInputTokens(10L)
        .setOutputTokens(5L)
        .setModelCalls(1)
        .setToolCalls(1);
    ENGINE.writeRecords(
        RecordToWrite.command()
            .key(agentInstanceKey)
            .agentInstance(AgentInstanceIntent.UPDATE, commandRecord));

    // then — the gate queues the internal command; it will be drained when the instance resumes
    final Record<RecordValue> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.BUFFERED_COMMAND)
            .withIntent(BufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var v = (BufferedCommandRecordValue) r.getValue();
                  return v.getProcessInstanceKey() == processInstanceKey
                      && v.getValueType() == ValueType.AGENT_INSTANCE;
                })
            .getFirst();
    assertThat(((BufferedCommandRecordValue) buffered.getValue()).getIntent())
        .isEqualTo(AgentInstanceIntent.UPDATE);
  }

  @Test
  public void shouldProcessCompleteCommandWhileSuspendedOnCancel() {
    // given
    final long elementInstanceKey = activateServiceTask();
    final long processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withRecordKey(elementInstanceKey)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "sys")
            .withLimits(100L, 5, 5)
            .create()
            .getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when
    ENGINE.agentInstances().withProcessInstanceKey(processInstanceKey).complete();

    // then — COMPLETE is classified PROCESS so the suspended process instance completes its agent
    // instance.
    final var completed =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
            .withRecordKey(agentInstanceKey)
            .getFirst();
    assertThat(completed).isNotNull();
  }

  private static long activateServiceTask() {
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(taskId, t -> t.zeebeJobType("agent").zeebeAiAgentTaskDefinition())
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .getFirst()
        .getKey();
  }
}
