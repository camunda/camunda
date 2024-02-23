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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class InterruptingEventSubprocessConcurrencyTest {

  private static final String PROCESS_ID = "proc";
  private static final String MSG_NAME = "messageName";

  @Rule
  public final EngineRule engineRule =
      EngineRule.singlePartition()
          // Disable batch processing. Interrupting behaviour is only reproducible if
          // process instance is not completed in one batch.
          .maxCommandsInBatch(1);

  @Test
  // https://github.com/camunda/zeebe/issues/6552
  public void shouldEndProcess() {
    // given
    final ProcessBuilder process = Bpmn.createExecutableProcess(PROCESS_ID);

    process
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .interrupting(true)
        .message(b -> b.name(MSG_NAME).zeebeCorrelationKeyExpression("key"))
        .endEvent("event_sub_end");

    final BpmnModelInstance model =
        process
            .startEvent("start_proc")
            .intermediateCatchEvent("catch")
            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
            .exclusiveGateway()
            .endEvent("end_proc")
            .done();

    engineRule.deployment().withXmlResource(model).deploy();

    final long processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123))
            .create();

    // when
    final var intermediateSubscriptionCreated =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName("msg")
            .getFirst();

    final var eventSubprocessSubscriptionCreated =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withMessageName(MSG_NAME)
            .getFirst();

    engineRule.writeRecords(
        RecordToWrite.command()
            .processMessageSubscription(
                ProcessMessageSubscriptionIntent.CORRELATE,
                intermediateSubscriptionCreated.getValue()),
        RecordToWrite.command()
            .processMessageSubscription(
                ProcessMessageSubscriptionIntent.CORRELATE,
                eventSubprocessSubscriptionCreated.getValue()));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  // https://github.com/camunda-cloud/zeebe/issues/6587
  public void shouldInterruptBoundaryEvent() {
    // given
    final ProcessBuilder process = Bpmn.createExecutableProcess(PROCESS_ID);

    process
        .eventSubProcess("event_sub_proc")
        .startEvent("event_sub_start")
        .interrupting(true)
        .message(b -> b.name(MSG_NAME).zeebeCorrelationKeyExpression("key"))
        .endEvent("event_sub_end");

    final BpmnModelInstance model =
        process
            .startEvent("start_proc")
            .sequenceFlowId("toParallel")
            .serviceTask("serviceTask", s -> s.zeebeJobType("task"))
            .boundaryEvent("boundary")
            .cancelActivity(true)
            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
            .endEvent("end_proc")
            .done();

    engineRule.deployment().withXmlResource(model).deploy();

    final long processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123))
            .create();

    // when
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName("msg")
        .await();

    engineRule.writeRecords(
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord().setName("msg").setCorrelationKey("123").setTimeToLive(0)),
        RecordToWrite.command()
            .message(
                MessageIntent.PUBLISH,
                new MessageRecord().setName(MSG_NAME).setCorrelationKey("123").setTimeToLive(0)));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getBpmnElementType(), r.getIntent()))
        .containsSubsequence(
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.EVENT_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
