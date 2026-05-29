/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MessageCorrelationAgentToolNameTest {

  private static final String PROCESS_ID = "processWithMessageStartEvent";
  private static final String START_MSG_NAME = "startMsg";
  private static final String START_EVENT_ID = "msgStartEvent";
  private static final String AGENT_TOOL_NAME = "my-agent-tool";
  private static final String CORRELATION_KEY = "correlationKey";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void deployProcess() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent(START_EVENT_ID)
                .message(m -> m.name(START_MSG_NAME))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldPropagateAgentToolNameOnCorrelationRecord() {
    // when
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .withAgentToolName(AGENT_TOOL_NAME)
        .correlate();

    // then
    final var correlatedRecord =
        RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
            .withName(START_MSG_NAME)
            .getFirst();

    assertThat(correlatedRecord.getValue().getAgentToolName()).isEqualTo(AGENT_TOOL_NAME);
  }

  @Test
  public void shouldSetAgentInfoOnProcessInstanceRecordWhenAgentToolNameIsSet() {
    // when
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .withAgentToolName(AGENT_TOOL_NAME)
        .correlate();

    // then
    final var processActivatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(processActivatingRecord.getAgent()).isNotNull();
    assertThat(processActivatingRecord.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);
  }

  @Test
  public void shouldNotSetAgentInfoWhenAgentToolNameIsAbsent() {
    // when
    engine.messageCorrelation().withName(START_MSG_NAME).withCorrelationKey("").correlate();

    // then
    final var processActivatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(processActivatingRecord.getAgent()).isNull();
  }

  @Test
  public void shouldSetAgentInfoOnAllFollowUpProcessInstanceRecords() {
    // when
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .withAgentToolName(AGENT_TOOL_NAME)
        .correlate();

    // then - every record in the created process instance carries agent tracing info
    final var processInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .allSatisfy(
            record -> {
              assertThat(record.getAgent()).isNotNull();
              assertThat(record.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);
            });
  }

  @Test
  public void shouldSetAgentInfoWhenOnlyIntermediateCatchEventIsCorrelated() {
    // given - a process instance waiting at an intermediate catch event (no start event sub)
    final var catchProcessId = "catchProcess";
    final var catchEventId = "msgCatch";
    engine
        .deployment()
        .withXmlResource(
            "catch-process.bpmn",
            Bpmn.createExecutableProcess(catchProcessId)
                .startEvent()
                .intermediateCatchEvent(
                    catchEventId,
                    e ->
                        e.message(
                            m ->
                                m.name("catchMsg")
                                    .zeebeCorrelationKey("=\"%s\"".formatted(CORRELATION_KEY))))
                .endEvent()
                .done())
        .deploy();
    final var catchProcessInstanceKey =
        engine.processInstance().ofBpmnProcessId(catchProcessId).create();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(catchProcessInstanceKey)
        .await();

    // when - correlate with agentToolName, no start event subscription exists
    engine
        .messageCorrelation()
        .withName("catchMsg")
        .withCorrelationKey(CORRELATION_KEY)
        .withAgentToolName(AGENT_TOOL_NAME)
        .correlate();

    // then - follow-up records written during correlation carry agent info
    final var completedRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(catchProcessInstanceKey)
            .withElementId(catchEventId)
            .getFirst();
    assertThat(completedRecord.getAgent()).isNotNull();
    assertThat(completedRecord.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);
  }

  @Test
  public void shouldSetAgentInfoWhenMultipleProcessesShareTheSameStartMessage() {
    // given - a second process also started by the same message name
    final var secondProcessId = "secondProcessWithSameStartMsg";
    final var secondStartEventId = "secondStartEvent";
    engine
        .deployment()
        .withXmlResource(
            "process2.bpmn",
            Bpmn.createExecutableProcess(secondProcessId)
                .startEvent(secondStartEventId)
                .message(m -> m.name(START_MSG_NAME))
                .endEvent()
                .done())
        .deploy();

    // when - one correlate command creates instances in both processes
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .withAgentToolName(AGENT_TOOL_NAME)
        .correlate();

    // then - both created process instances carry agent tracing
    final var process1Record =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    final var process2Record =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(secondProcessId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(process1Record.getAgent()).isNotNull();
    assertThat(process1Record.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);
    assertThat(process2Record.getAgent()).isNotNull();
    assertThat(process2Record.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);
  }

  @Test
  public void
      shouldSetAgentInfoOnBothStartEventAndIntermediateCatchEventWhenSameMessageIsCorrelated() {
    // given - an existing process instance waiting at an intermediate catch event
    //         for the same message name as the start event
    final var catchProcessId = "catchProcess";
    final var catchEventId = "msgCatch";
    engine
        .deployment()
        .withXmlResource(
            "catch-process.bpmn",
            Bpmn.createExecutableProcess(catchProcessId)
                .startEvent()
                .intermediateCatchEvent(
                    catchEventId,
                    e ->
                        e.message(
                            m ->
                                m.name(START_MSG_NAME)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(CORRELATION_KEY))))
                .endEvent()
                .done())
        .deploy();
    final var catchProcessInstanceKey =
        engine.processInstance().ofBpmnProcessId(catchProcessId).create();

    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(catchProcessInstanceKey)
        .await();

    // when - correlate triggers both the start event (new process) and the intermediate catch
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withAgentToolName(AGENT_TOOL_NAME)
        .correlate();

    // then - the newly created process instance (from start event) carries agent info
    final var startEventProcessRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();
    assertThat(startEventProcessRecord.getAgent()).isNotNull();
    assertThat(startEventProcessRecord.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);

    // then - the intermediate catch event continuation also carries agent info
    final var catchEventRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(catchProcessInstanceKey)
            .withElementId(catchEventId)
            .getFirst();
    assertThat(catchEventRecord.getAgent()).isNotNull();
    assertThat(catchEventRecord.getAgent().getToolName()).isEqualTo(AGENT_TOOL_NAME);
  }
}
