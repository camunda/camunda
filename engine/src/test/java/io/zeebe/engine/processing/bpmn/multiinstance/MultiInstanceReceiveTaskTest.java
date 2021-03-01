/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
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

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask(
              ELEMENT_ID,
              t ->
                  t.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression(INPUT_ELEMENT))
                      .multiInstance(
                          b ->
                              b.zeebeInputCollectionExpression(INPUT_COLLECTION)
                                  .zeebeInputElement(INPUT_ELEMENT)))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateOneMessageSubscriptionForEachElement() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList("a", "b", "c"))
            .create();

    // then
    final List<Long> elementInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(ELEMENT_ID)
            .skip(1)
            .limit(3)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withWorkflowInstanceKey(workflowInstanceKey)
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
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final List<String> inputCollection = Arrays.asList("a", "b", "c");

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
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
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limitToWorkflowInstanceCompleted()
                .withElementId(ELEMENT_ID))
        .hasSize(4);

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .filterRootScope()
                .limitToWorkflowInstanceCompleted())
        .extracting(Record::getIntent)
        .contains(WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCloseMessageSubscriptionOnTermination() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, Arrays.asList(10, 20, 30))
            .create();

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .limit(3)
        .exists();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.DELETED)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(3))
        .hasSize(3);
  }
}
