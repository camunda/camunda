/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * A batch-originated MODIFY propagates its batchOperationReference to every follow-up command,
 * which makes a signal broadcast triggered by process execution look like a user command. It must
 * still be delivered, as it is for a single-instance modification.
 */
public final class BatchMoveTokenSignalAuthTest extends AbstractBatchOperationTest {

  private static final String SIGNAL_NAME = "batch-move-signal";
  private static final String SOURCE_ELEMENT = "source";

  @Test
  public void shouldDeliverSignalThrownAfterBatchMoveOntoGateway() {
    // given
    final var senderId = "sender-" + UUID.randomUUID();
    final var receiverId = "receiver-" + UUID.randomUUID();
    final var signalName = SIGNAL_NAME + UUID.randomUUID();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(senderId)
                .startEvent()
                .userTask(SOURCE_ELEMENT)
                .zeebeUserTask()
                .exclusiveGateway("target")
                .intermediateThrowEvent("throw", t -> t.signal(signalName))
                .userTask("after")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(receiverId)
                .startEvent("signalStart")
                .signal(signalName)
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(senderId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when
    createNewModifyProcessInstanceBatchOperation(
        Set.of(processInstanceKey), SOURCE_ELEMENT, "target", claims());

    // then
    assertMovedAndSignalDelivered(processInstanceKey, signalName, receiverId);
  }

  @Test
  public void shouldDeliverSignalOfEndEventAfterBatchMove() {
    // given
    final var senderId = "sender-" + UUID.randomUUID();
    final var receiverId = "receiver-" + UUID.randomUUID();
    final var signalName = SIGNAL_NAME + UUID.randomUUID();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(senderId)
                .startEvent()
                .userTask(SOURCE_ELEMENT)
                .zeebeUserTask()
                .endEvent("target")
                .signal(signalName)
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(receiverId)
                .startEvent("signalStart")
                .signal(signalName)
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(senderId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when
    createNewModifyProcessInstanceBatchOperation(
        Set.of(processInstanceKey), SOURCE_ELEMENT, "target", claims());

    // then
    assertMovedAndSignalDelivered(processInstanceKey, signalName, receiverId);
  }

  private Map<String, Object> claims() {
    return Map.of(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }

  @Test
  public void shouldRejectBatchMoveToUnknownElementWithBatchReference() {
    // given
    final var senderId = "sender-" + UUID.randomUUID();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(senderId)
                .startEvent()
                .userTask(SOURCE_ELEMENT)
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(senderId).create();
    RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();

    // when
    createNewModifyProcessInstanceBatchOperation(
        Set.of(processInstanceKey), SOURCE_ELEMENT, "unknown-" + UUID.randomUUID(), claims());

    // then - the exporters derive the batch item state FAILED from this rejection
    final var rejection =
        RecordingExporter.processInstanceModificationRecords()
            .onlyCommandRejections()
            .withIntent(ProcessInstanceModificationIntent.MODIFY)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(rejection.getBatchOperationReference()).isPositive();
  }

  private void assertMovedAndSignalDelivered(
      final long processInstanceKey, final String signalName, final String receiverId) {
    assertThat(
            RecordingExporter.processInstanceModificationRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withIntent(ProcessInstanceModificationIntent.MODIFIED)
                .exists())
        .describedAs("modification is applied, not rolled back")
        .isTrue();

    assertThat(
            RecordingExporter.signalRecords(SignalIntent.BROADCASTED)
                .filter(r -> r.getValue().getSignalName().equals(signalName))
                .exists())
        .describedAs("signal is broadcast")
        .isTrue();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withBpmnProcessId(receiverId)
                .filterRootScope()
                .exists())
        .describedAs("signal start process instance is created")
        .isTrue();
  }
}
