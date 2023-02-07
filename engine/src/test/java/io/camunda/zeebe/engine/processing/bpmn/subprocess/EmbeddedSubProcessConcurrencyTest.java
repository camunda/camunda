/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.EmbeddedSubProcessBuilder;
import io.camunda.zeebe.model.bpmn.builder.EventSubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessEventIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EmbeddedSubProcessConcurrencyTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          // Disable batch processing. Interrupting behaviour is only reproducible if
          // process instance is not completed in one batch.
          .maxCommandsInBatch(1);

  private static final String PROCESS_ID = "process-with-sub-process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotTriggerBoundaryEventWhenFlowscopeIsInterrupted() {
    // given
    final Consumer<EmbeddedSubProcessBuilder> subProcessBuilder =
        subprocess ->
            subprocess
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType("task"))
                .endEvent()
                .moveToActivity("subProcess")
                .boundaryEvent(
                    "msgBoundary",
                    boundary ->
                        boundary
                            .cancelActivity(true)
                            .message(
                                msg ->
                                    msg.name("boundary")
                                        .zeebeCorrelationKeyExpression("correlationKey")))
                .endEvent()
                .done();

    final Consumer<EventSubProcessBuilder> eventSubProcessBuilder =
        eventSubProcess ->
            eventSubProcess
                .startEvent("eventSubProcessStartEvent")
                .message(
                    m -> m.name("eventSubProcess").zeebeCorrelationKeyExpression("correlationKey"))
                .endEvent();

    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .eventSubProcess("eventSubProcess", eventSubProcessBuilder)
                .startEvent()
                .subProcess(
                    "subProcess",
                    subProcess -> subProcessBuilder.accept(subProcess.embeddedSubProcess()))
                .endEvent()
                .done())
        .deploy();

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("correlationKey", "correlationKey")
            .create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task")
        .await();

    // when
    // We need to make sure no records are written in between the publish commands. This could
    // cause the test to become flaky.
    ENGINE.writeRecords(
        // First we publish a message to try and trigger the boundary event
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName("boundary")
                    .setTimeToLive(0L)
                    .setCorrelationKey("correlationKey")
                    .setVariables(BufferUtil.wrapString(""))),
        // Next we publish a message to trigger the event sub process. This will interrupt the
        // flow scope of the sub process, whilst the subprocess is being terminated because of the
        // boundary event
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord()
                    .setName("eventSubProcess")
                    .setTimeToLive(0L)
                    .setCorrelationKey("correlationKey")
                    .setVariables(BufferUtil.wrapString(""))));

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .filter(r -> r.getValueType() == ValueType.PROCESS_EVENT)
                .withIntent(ProcessEventIntent.TRIGGERING))
        .extracting(r -> ((ProcessEventRecordValue) r.getValue()).getTargetElementId())
        .containsExactly("msgBoundary", "eventSubProcessStartEvent");

    // No event should be TRIGGERED. We don't want to trigger the boundary event.
    // The event sub process does not write a TRIGGERED event.
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .withIntent(ProcessEventIntent.TRIGGERED))
        .isEmpty();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.SERVICE_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.START_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .contains(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }
}
