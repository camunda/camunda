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
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;

/**
 * Reproduction test for <a href="https://github.com/camunda/camunda/issues/61099">#61099</a>'s
 * additional case: a subscription still {@code OPENING} when the instance is suspended, whose
 * message-side CREATE immediately correlates an already-buffered TTL message instead of sending a
 * plain open-acknowledgement, live-locks instead of being driven to the durable resume manifest.
 */
public final class MessageSuspensionOpeningSubscriptionRaceTest {

  private static final int PARTITION_COUNT = 3;
  private static final int PROCESS_INSTANCE_PARTITION = 1;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDriveOpeningSubscriptionToResumeManifestInsteadOfChurning() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = correlationKeyForPartitionOtherThan(PROCESS_INSTANCE_PARTITION);
    final int subscriptionPartition = getSubscriptionPartitionId(correlationKey);

    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();

    // the message is already buffered before the subscription ever opens, so the message
    // partition's CREATE handling finds it immediately and correlates instead of acknowledging
    engine
        .message()
        .onPartition(subscriptionPartition)
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();

    // capture the CORRELATE sent in place of the open-ack, instead of letting it reach the PI
    // partition, so the instance can be suspended while the subscription is still OPENING
    final var capturedCorrelate = new AtomicReference<ProcessMessageSubscriptionRecord>();
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (receiverPartitionId == PROCESS_INSTANCE_PARTITION
              && intent == ProcessMessageSubscriptionIntent.CORRELATE) {
            capturedCorrelate.set((ProcessMessageSubscriptionRecord) command);
            return false;
          }
          return true;
        });

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .onPartition(PROCESS_INSTANCE_PARTITION)
            .create();

    // confirms the race: the message partition correlated the buffered message on CREATE
    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // suspend while the subscription is still OPENING — the suspend pass skips it, since there
    // is no confirmed message-side row yet to close
    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> true);

    assertThat(capturedCorrelate.get())
        .as("interceptor should have captured a CORRELATE sent instead of the open-ack")
        .isNotNull();

    // when — release the captured CORRELATE onto the now-suspended, still-OPENING subscription
    engine.writeCommandOnPartition(
        PROCESS_INSTANCE_PARTITION,
        -1L,
        ProcessMessageSubscriptionIntent.CORRELATE,
        capturedCorrelate.get());

    // then — driven to the durable resume manifest (DELETING/closedForSuspend), not left OPENING
    final var deleting =
        RecordingExporter.processMessageSubscriptionRecords(
                ProcessMessageSubscriptionIntent.DELETING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(deleting.getValue().isClosedForSuspend()).isTrue();

    // resume drains the manifest's buffered REOPEN, re-subscribing to the still-buffered message
    engine.processInstance().withInstanceKey(processInstanceKey).resume();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
  }

  private String correlationKeyForPartitionOtherThan(final int partitionId) {
    for (int i = 0; ; i++) {
      final String candidate = "key-" + i;
      if (getSubscriptionPartitionId(candidate) != partitionId) {
        return candidate;
      }
    }
  }

  private int getSubscriptionPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = engine.getPartitionIds();
    return SubscriptionUtil.getSubscriptionPartitionId(
        io.camunda.zeebe.util.buffer.BufferUtil.wrapString(correlationKey), partitionIds.size());
  }
}
