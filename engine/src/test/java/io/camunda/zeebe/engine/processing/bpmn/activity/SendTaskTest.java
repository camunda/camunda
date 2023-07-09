/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.SendTaskBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class SendTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ID = "task";
  private static final String MESSAGE_NAME = "message";
  private static final String MESSAGE_ID = "messageId";
  private static final String CORRELATION_KEY = "correlationKey";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithSendTask(final Consumer<SendTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID).startEvent().sendTask(TASK_ID, modifier).done();
  }

  @Test
  public void shouldActivateTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t -> t.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_KEY))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.SEND_TASK)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.SEND_TASK)
            .getFirst();

    assertThat(taskActivating.getValue())
        .hasElementId(TASK_ID)
        .hasBpmnElementType(BpmnElementType.SEND_TASK)
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldCompleteTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t -> t.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_KEY))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SEND_TASK, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(BpmnElementType.SEND_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldPublishMessage() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t ->
                    t.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeCorrelationKey(CORRELATION_KEY)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeTimeToLive("PT10S"))))
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId(MESSAGE_ID)
                .withTimeToLive(10_000L)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldPublishMessageWithMessageNameExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t ->
                    t.message(
                        m ->
                            m.nameExpression("= name")
                                .zeebeCorrelationKey(CORRELATION_KEY)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeTimeToLive("PT10S"))))
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("name", "foo").create();

    // then
    Assertions.assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName("foo")
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId(MESSAGE_ID)
                .withTimeToLive(10_000L)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldPublishMessageWithMessageCorrelationKeyExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t ->
                    t.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeCorrelationKey("= correlationKey")
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeTimeToLive("PT10S"))))
        .deploy();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariable("correlationKey", "foo")
        .create();

    // then
    Assertions.assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey("foo")
                .withMessageId(MESSAGE_ID)
                .withTimeToLive(10_000L)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldPublishMessageWithMessageIdExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t ->
                    t.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeCorrelationKey(CORRELATION_KEY)
                                .zeebeMessageId("= messageId")
                                .zeebeTimeToLive("PT10S"))))
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("messageId", "foo").create();

    // then
    Assertions.assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId("foo")
                .withTimeToLive(10_000L)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldPublishMessageWithMessageTimeToLiveExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t ->
                    t.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeCorrelationKey(CORRELATION_KEY)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeTimeToLive("= timeToLive"))))
        .deploy();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withVariable("timeToLive", "PT10S")
        .create();

    // then
    Assertions.assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId(MESSAGE_ID)
                .withTimeToLive(10_000L)
                .limit(1))
        .hasSize(1);
  }

  @Test
  public void shouldTriggerMessageCatchEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                t ->
                    t.message(
                            m ->
                                m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(CORRELATION_KEY))
                        .intermediateCatchEvent(
                            "catch",
                            b ->
                                b.message(
                                    m ->
                                        m.name(MESSAGE_NAME)
                                            .zeebeCorrelationKeyExpression(CORRELATION_KEY)))
                        .endEvent()))
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("correlationKey", "foo")
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.SEND_TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.INTERMEDIATE_CATCH_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
