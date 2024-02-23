/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MultiInstanceReceiveTaskTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String MESSAGE_NAME = "test";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";
  private static final String OUTPUT_ELEMENT = "result";
  private static final String OUTPUT_COLLECTION = "results";

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask(
              ELEMENT_ID,
              t ->
                  t.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(INPUT_ELEMENT))
                      .multiInstance(
                          b ->
                              b.zeebeInputCollectionExpression(INPUT_COLLECTION)
                                  .zeebeInputElement(INPUT_ELEMENT)
                                  .zeebeOutputElementExpression(OUTPUT_ELEMENT)
                                  .zeebeOutputCollection(OUTPUT_COLLECTION)))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateOneMessageSubscriptionForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList("a", "b", "c"))
            .create();

    // then
    final List<Long> elementInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(ELEMENT_ID)
            .skip(1)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3)
        .extracting(Record::getValue)
        .extracting(r -> tuple(r.getCorrelationKey(), r.getElementInstanceKey()))
        .containsExactly(
            tuple("a", elementInstanceKey.get(0)),
            tuple("b", elementInstanceKey.get(1)),
            tuple("c", elementInstanceKey.get(2)));
  }

  @Test
  public void shouldCompleteBodyWhenAllMessagesAreCorrelated() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS).deploy();

    final List<String> inputCollection = Arrays.asList("a", "b", "c");

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // when
    inputCollection.forEach(
        element ->
            ENGINE
                .message()
                .withName(MESSAGE_NAME)
                .withCorrelationKey(element)
                .withTimeToLive(Duration.ofSeconds(3).toMillis())
                .publish());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .hasSize(4);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .filterRootScope()
                .limitToProcessInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCloseMessageSubscriptionOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(3)
        .exists();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withProcessInstanceKey(processInstanceKey)
                .limit(3))
        .hasSize(3);
  }

  @Test
  public void shouldCollectOutputFromChildInstance() {
    // given
    final var inputCollection = Arrays.asList(10, 20, 30);
    ENGINE.deployment().withXmlResource(PROCESS).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // when
    inputCollection.stream()
        .map(Objects::toString)
        .forEach(
            element ->
                ENGINE
                    .message()
                    .withName(MESSAGE_NAME)
                    .withCorrelationKey(element)
                    .withTimeToLive(Duration.ofSeconds(3).toMillis())
                    .withVariables(Map.of(OUTPUT_ELEMENT, element))
                    .publish());

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
        .await();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
                .variableRecords()
                .withName("results")
                .withScopeKey(processInstanceKey))
        .extracting(r -> r.getValue().getValue())
        .containsExactly("[\"10\",\"20\",\"30\"]");
  }
}
