/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.subprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.RecordToWrite;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class InterruptingEventSubprocessConcurrencyTest {

  private static final String PROCESS_ID = "proc";
  private static final String MSG_NAME = "messageName";

  @Rule public final EngineRule engineRule = EngineRule.singlePartition();

  @Test
  // https://github.com/camunda-cloud/zeebe/issues/6552
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
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(MSG_NAME)
        .await();
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

  @Test
  // https://github.com/camunda-cloud/zeebe/issues/6565
  public void shouldEndProcessWithParallelFlow() {
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
            .parallelGateway("parallel")
            .intermediateCatchEvent("catch")
            .message(m -> m.name("msg").zeebeCorrelationKeyExpression("key"))
            .endEvent("end_proc")
            .moveToLastGateway()
            .intermediateCatchEvent("catch1")
            .message(m -> m.name("msg1").zeebeCorrelationKeyExpression("key1"))
            .endEvent("end_proc1")
            .done();

    engineRule.deployment().withXmlResource(model).deploy();

    final long processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123, "key1", 123))
            .create();

    // when
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withMessageName(MSG_NAME)
        .await();
    RecordingExporter.processInstanceRecords()
        .withElementType(BpmnElementType.START_EVENT)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withElementId("start_proc")
        .withProcessInstanceKey(processInstanceKey)
        .await();

    engineRule
        .message()
        .withName(MSG_NAME)
        .withCorrelationKey("123")
        .withVariables(Map.of("key", "123"))
        .publish();

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
  public void shouldTerminateXorWithIncident() {
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
            .sequenceFlowId("toXor")
            .exclusiveGateway("xor")
            .condition("=yolo")
            .endEvent()
            .done();

    engineRule.deployment().withXmlResource(model).deploy();

    final long processInstanceKey =
        engineRule
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("key", 123, "key1", 123))
            .create();

    // when
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    engineRule
        .message()
        .withName(MSG_NAME)
        .withCorrelationKey("123")
        .withVariables(Map.of("key", "123"))
        .publish();

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
