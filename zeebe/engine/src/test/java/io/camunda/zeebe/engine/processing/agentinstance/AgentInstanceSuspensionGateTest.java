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
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBufferedCommandRecordValue;
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
 *   <li>Internal commands (engine-generated follow-ups) are BUFFER'd so they can be replayed when
 *       the instance resumes.
 *   <li>External commands (from REST/gRPC clients) would be REJECT'd to prevent an authorization
 *       bypass: a buffered command is drained as an internal command, skipping the CSL check.
 * </ul>
 *
 * <p>COMPLETE is classified {@code PROCESS} so that teardown bookkeeping is not orphaned when a
 * suspended instance is cancelled.
 *
 * <p>The suspension gate resolves the process instance key from the command record. For CREATE and
 * UPDATE, {@code processInstanceKey} must be present on the record for the gate to fire. Production
 * clients only carry {@code elementInstanceKey} on these commands, so the gate returns PROCESS
 * early for real external commands; the tests below supply {@code processInstanceKey} explicitly
 * via {@link RecordToWrite} to exercise the gate. {@link RecordToWrite#command()} produces internal
 * commands (no request metadata), so the BUFFER path is exercised rather than REJECT.
 */
public class AgentInstanceSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBufferInternalCreateCommandWhileSuspended() {
    // given
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
    final var serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    final long elementInstanceKey = serviceTaskActivated.getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — supply processInstanceKey explicitly so the suspension gate can resolve the target PI.
    // RecordToWrite.command() produces an internal command (no request metadata), so the gate
    // classifies it as BUFFER (not REJECT, which is the path for external commands).
    final var commandRecord =
        new AgentInstanceRecord()
            .setElementInstanceKey(elementInstanceKey)
            .setProcessInstanceKey(processInstanceKey);
    ENGINE.writeRecords(
        RecordToWrite.command().agentInstance(AgentInstanceIntent.CREATE, commandRecord));

    // then — the gate queues the internal command; it will be drained when the instance resumes
    final Record<RecordValue> buffered =
        RecordingExporter.records()
            .withValueType(ValueType.PROCESS_INSTANCE_BUFFERED_COMMAND)
            .withIntent(ProcessInstanceBufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var v = (ProcessInstanceBufferedCommandRecordValue) r.getValue();
                  return v.getProcessInstanceKey() == processInstanceKey
                      && v.getValueType() == ValueType.AGENT_INSTANCE;
                })
            .getFirst();
    assertThat(((ProcessInstanceBufferedCommandRecordValue) buffered.getValue()).getIntent())
        .isEqualTo(AgentInstanceIntent.CREATE);
  }

  @Test
  public void shouldBufferInternalUpdateCommandWhileSuspended() {
    // given
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
    final var serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    final long elementInstanceKey = serviceTaskActivated.getKey();

    final long agentInstanceKey =
        ENGINE
            .agentInstances()
            .withElementInstanceKey(elementInstanceKey)
            .withDefinition("gpt-4o", "openai", "sys")
            .withLimits(100L, 5, 5)
            .create()
            .getKey();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — supply processInstanceKey explicitly so the suspension gate can resolve the target PI.
    // RecordToWrite.command() produces an internal command, so the gate classifies it as BUFFER.
    final var commandRecord =
        new AgentInstanceRecord()
            .setAgentInstanceKey(agentInstanceKey)
            .setProcessInstanceKey(processInstanceKey)
            .setChangedAttributes(List.of("metrics"));
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
            .withValueType(ValueType.PROCESS_INSTANCE_BUFFERED_COMMAND)
            .withIntent(ProcessInstanceBufferedCommandIntent.BUFFERED)
            .filter(
                r -> {
                  final var v = (ProcessInstanceBufferedCommandRecordValue) r.getValue();
                  return v.getProcessInstanceKey() == processInstanceKey
                      && v.getValueType() == ValueType.AGENT_INSTANCE;
                })
            .getFirst();
    assertThat(((ProcessInstanceBufferedCommandRecordValue) buffered.getValue()).getIntent())
        .isEqualTo(AgentInstanceIntent.UPDATE);
  }

  @Test
  public void shouldProcessCompleteCommandWhileSuspendedOnCancel() {
    // given
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
    final var serviceTaskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();
    final long elementInstanceKey = serviceTaskActivated.getKey();

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
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then — cancelling a suspended instance with an active agent instance emits
    // AGENT_INSTANCE:COMPLETED. COMPLETE is classified PROCESS so that the processor is not
    // gated by the suspension marker.
    final var completed =
        RecordingExporter.agentInstanceRecords(AgentInstanceIntent.COMPLETED)
            .withRecordKey(agentInstanceKey)
            .getFirst();
    assertThat(completed).isNotNull();
  }
}
