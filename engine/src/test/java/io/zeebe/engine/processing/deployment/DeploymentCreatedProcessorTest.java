/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class DeploymentCreatedProcessorTest {

  public static final String PROCESS_ID = "process";
  public static final String RESOURCE_ID = "process.bpmn";
  public static final String MESSAGE_NAME = "msg";

  @Rule
  public final StreamProcessorRule rule =
      new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION + 1);

  private List<Long> processedRecordPositions;

  @Before
  public void setUp() {
    processedRecordPositions = new ArrayList<>();

    rule.startTypedStreamProcessor(
        (typedRecordProcessors, processingContext) -> {
          final var zeebeState = processingContext.getZeebeState();
          final var workflowState = zeebeState.getWorkflowState();

          DeploymentEventProcessors.addDeploymentCreateProcessor(
              typedRecordProcessors,
              workflowState,
              (key, partition) -> {},
              Protocol.DEPLOYMENT_PARTITION + 1);
          typedRecordProcessors.onEvent(
              ValueType.DEPLOYMENT,
              DeploymentIntent.CREATED,
              new DeploymentCreatedProcessor(workflowState, false));
          return typedRecordProcessors;
        },
        processedRecord -> processedRecordPositions.add(processedRecord.getPosition()));
  }

  @Test
  public void shouldNotFailIfCantFindPreviousVersion() {
    // given

    // when
    writeMessageStartRecord(1, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().exists());
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .limit(1)
                .getFirst()
                .getIntent())
        .isEqualTo(MessageStartEventSubscriptionIntent.OPEN);
  }

  @Test
  public void shouldNotWriteCloseSubscriptionIfNotMessageStart() {
    // given

    // when
    writeNoneStartRecord(3, 1);
    writeMessageStartRecord(7, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().exists());
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .limit(1)
                .getFirst()
                .getIntent())
        .isEqualTo(MessageStartEventSubscriptionIntent.OPEN);
  }

  @Test
  public void shouldCloseSubscriptionWhenInCorrectOrder() {
    // given

    // when
    writeMessageStartRecord(3, 1);
    waitUntil(
        () -> rule.events().onlyDeploymentRecords().withIntent(DeploymentIntent.CREATED).exists());
    writeNoneStartRecord(7, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().count() == 2);

    final Record<MessageStartEventSubscriptionRecord> closeRecord =
        rule.events()
            .onlyMessageStartEventSubscriptionRecords()
            .withIntent(MessageStartEventSubscriptionIntent.CLOSE)
            .getFirst();

    Assertions.assertThat(closeRecord.getValue().getWorkflowKey()).isEqualTo(3);
  }

  @Test
  public void shouldIgnoreOutdatedDeployment() {
    // given

    // when
    writeMessageStartRecord(5, 2);
    waitUntil(
        () ->
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.OPEN)
                .exists());

    writeMessageStartRecord(3, 1);

    waitUntil(
        () -> {
          final var deploymentCreated =
              rule.events()
                  .onlyDeploymentRecords()
                  .withIntent(DeploymentIntent.CREATED)
                  .filter(d -> d.getKey() == 3)
                  .findFirst();

          // need to wait until the event is processed completely because the state is updated after
          // the event is written
          return deploymentCreated
              .map(record -> processedRecordPositions.contains(record.getPosition()))
              .orElse(false);
        });

    // then
    Assertions.assertThat(
            rule.getZeebeState()
                .getWorkflowState()
                .getLatestWorkflowVersionByProcessId(BufferUtil.wrapString(PROCESS_ID))
                .getVersion())
        .isEqualTo(2);
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.OPEN)
                .count())
        .isEqualTo(1);
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CLOSE))
        .isNullOrEmpty();
  }

  @Test
  public void shouldCloseSubscriptionEvenIfNotNextVersion() {
    // given

    // when
    writeMessageStartRecord(3, 1);
    waitUntil(
        () ->
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.OPEN)
                .exists());

    writeNoneStartRecord(7, 3);
    waitUntil(
        () ->
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CLOSE)
                .exists());

    writeMessageStartRecord(5, 2);
    waitUntil(
        () -> rule.events().onlyDeploymentRecords().withIntent(DeploymentIntent.CREATED).exists());

    // then
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CLOSE)
                .count())
        .isEqualTo(1);
  }

  private void writeNoneStartRecord(final long key, final int version) {
    writeNoneStartRecord(PROCESS_ID, RESOURCE_ID, key, version);
  }

  private void writeNoneStartRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final DeploymentRecord record =
        createNoneStartDeploymentRecord(processId, resourceId, key, version);

    rule.writeCommand(key, DeploymentIntent.CREATE, record);
  }

  private void writeMessageStartRecord(final long key, final int version) {
    writeMessageStartRecord(PROCESS_ID, RESOURCE_ID, key, version);
  }

  private void writeMessageStartRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final DeploymentRecord msgRecord =
        createMessageStartDeploymentRecord(processId, resourceId, key, version);
    rule.writeCommand(key, DeploymentIntent.CREATE, msgRecord);
  }

  private static DeploymentRecord createMessageStartDeploymentRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .message(MESSAGE_NAME)
            .endEvent()
            .done();
    return createDeploymentRecord(modelInstance, processId, resourceId, key, version);
  }

  private static DeploymentRecord createNoneStartDeploymentRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    return createDeploymentRecord(modelInstance, processId, resourceId, key, version);
  }

  private static DeploymentRecord createDeploymentRecord(
      final BpmnModelInstance modelInstance,
      final String processId,
      final String resourceId,
      final long key,
      final int version) {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceId))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)));

    deploymentRecord
        .workflows()
        .add()
        .setKey(key)
        .setBpmnProcessId(processId)
        .setResourceName(resourceId)
        .setVersion(version);

    return deploymentRecord;
  }
}
