/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.SendTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValueAssert;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class SendTaskIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ELEMENT_ID = "task";
  private static final String MESSAGE_NAME = "message";
  private static final String MESSAGE_ID = "messageId";
  private static final String CORRELATION_KEY = "correlationKey";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance processWithSendTask(final Consumer<SendTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .sendTask(TASK_ELEMENT_ID, modifier)
        .endEvent()
        .done();
  }

  private IncidentRecordValueAssert assertIncidentCreated(
      final long processInstanceKey, final long elementInstanceKey) {
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    return Assertions.assertThat(incidentRecord.getValue())
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(elementInstanceKey)
        .hasJobKey(-1L)
        .hasVariableScopeKey(elementInstanceKey);
  }

  @Test
  public void shouldCreateIncidentIfMessageNameExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m -> m.nameExpression("MISSING_VAR").zeebeCorrelationKey(CORRELATION_KEY))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var sendTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.SEND_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, sendTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldResolveIncidentAfterMessageNameExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m -> m.nameExpression("MISSING_VAR").zeebeCorrelationKey(CORRELATION_KEY))))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", MESSAGE_NAME)))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .limit(1))
        .hasSize(1);

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldCreateIncidentIfMessageIdExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeMessageIdExpression("MISSING_VAR")
                                .zeebeCorrelationKey(CORRELATION_KEY))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var sendTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.SEND_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, sendTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldResolveIncidentAfterMessageIdExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeMessageIdExpression("MISSING_VAR")
                                .zeebeCorrelationKey(CORRELATION_KEY))))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", MESSAGE_ID)))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId(MESSAGE_ID)
                .limit(1))
        .hasSize(1);

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldCreateIncidentIfMessageCorrelationKeyExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeCorrelationKeyExpression("MISSING_VAR"))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var sendTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.SEND_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, sendTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldResolveIncidentAfterMessageCorrelationKeyExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeCorrelationKeyExpression("MISSING_VAR"))))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", CORRELATION_KEY)))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId(MESSAGE_ID)
                .limit(1))
        .hasSize(1);

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldCreateIncidentIfMessageTimeToLiveExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeCorrelationKey(CORRELATION_KEY)
                                .zeebeTimeToLiveExpression("MISSING_VAR"))))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var sendTaskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.SEND_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, sendTaskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'MISSING_VAR': no variable found for name 'MISSING_VAR'");
  }

  @Test
  public void shouldResolveIncidentAfterMessageTimeToLiveExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithSendTask(
                s ->
                    s.message(
                        m ->
                            m.name(MESSAGE_NAME)
                                .zeebeMessageId(MESSAGE_ID)
                                .zeebeCorrelationKey(CORRELATION_KEY)
                                .zeebeTimeToLiveExpression("MISSING_VAR"))))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("MISSING_VAR", "PT10S")))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.messageRecords(MessageIntent.PUBLISH)
                .withName(MESSAGE_NAME)
                .withCorrelationKey(CORRELATION_KEY)
                .withMessageId(MESSAGE_ID)
                .withTimeToLive(10_000L)
                .limit(1))
        .hasSize(1);

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }
}
