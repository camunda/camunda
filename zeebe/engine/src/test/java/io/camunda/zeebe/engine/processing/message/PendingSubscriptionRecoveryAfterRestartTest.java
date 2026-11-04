/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Level;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class PendingSubscriptionRecoveryAfterRestartTest {

  private static final int PROCESS_INSTANCE_PARTITION = 1;
  private static final int MESSAGE_SUBSCRIPTION_PARTITION = 2;
  private static final String CORRELATION_KEY = "test-2";
  // with a batch limit of 1, each instance is recovered in its own chunk
  private static final int INSTANCES = 3;
  // past the checker interval, and the recovered entries are then older than the resend timeout
  private static final Duration CHECKER_RUN = Duration.ofMinutes(1);

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(2)
          .withEngineConfig(c -> c.setMessagesPendingSubscriptionRecoveryBatchLimit(1));

  private String messageName;

  @Before
  public void setUp() {
    assertThat(
            SubscriptionUtil.getSubscriptionPartitionId(
                BufferUtil.wrapString(CORRELATION_KEY), engine.getPartitionIds().size()))
        .isEqualTo(MESSAGE_SUBSCRIPTION_PARTITION);

    messageName = "message-" + UUID.randomUUID();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .intermediateCatchEvent("catch")
                .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("key"))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldResendOpensThatWereLostBeforeRestart() {
    // given - process instances whose subscription opens never reach the message partition
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(receiverPartitionId == MESSAGE_SUBSCRIPTION_PARTITION
                && valueType == ValueType.MESSAGE_SUBSCRIPTION
                && intent == MessageSubscriptionIntent.CREATE));

    final var processInstanceKeys = createProcessInstances();
    processInstanceKeys.forEach(
        processInstanceKey ->
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATING)
                .withProcessInstanceKey(processInstanceKey)
                .await());

    // when - the engine restarts, which also stops dropping the opens, and the checker runs
    restartAndAwaitRecovery();
    engine.increaseTime(CHECKER_RUN);

    // then - the recovered pending subscriptions made the checker resend every open
    processInstanceKeys.forEach(
        processInstanceKey ->
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CREATED)
                .withPartitionId(MESSAGE_SUBSCRIPTION_PARTITION)
                .withProcessInstanceKey(processInstanceKey)
                .await());
  }

  @Test
  public void shouldResendCorrelationsThatWereLostBeforeRestart() {
    // given - opened subscriptions whose correlations never reach the process partition
    final var processInstanceKeys = createProcessInstances();
    processInstanceKeys.forEach(
        processInstanceKey ->
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .await());

    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(receiverPartitionId == PROCESS_INSTANCE_PARTITION
                && valueType == ValueType.PROCESS_MESSAGE_SUBSCRIPTION
                && intent == ProcessMessageSubscriptionIntent.CORRELATE));

    processInstanceKeys.forEach(
        processInstanceKey -> {
          engine.message().withName(messageName).withCorrelationKey(CORRELATION_KEY).publish();
          RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CORRELATING)
              .withProcessInstanceKey(processInstanceKey)
              .await();
        });

    // when - the engine restarts, which also stops dropping the correlations, and the checker runs
    restartAndAwaitRecovery();
    engine.increaseTime(CHECKER_RUN);

    // then - the recovered pending subscriptions made the checker resend every correlation
    processInstanceKeys.forEach(
        processInstanceKey ->
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CORRELATED)
                .withPartitionId(PROCESS_INSTANCE_PARTITION)
                .withProcessInstanceKey(processInstanceKey)
                .await());
  }

  private List<Long> createProcessInstances() {
    return IntStream.range(0, INSTANCES)
        .mapToObj(
            i ->
                engine
                    .processInstance()
                    .ofBpmnProcessId("process")
                    .onPartition(PROCESS_INSTANCE_PARTITION)
                    .withVariable("key", CORRELATION_KEY)
                    .create())
        .toList();
  }

  // Restarts from a snapshot of every partition, so the pending subscriptions are only in the
  // snapshot and replay doesn't rebuild them; only the recovery can. The recovery runs in the
  // background, so this waits for both sides of every partition to log that they are done.
  private void restartAndAwaitRecovery() {
    try (final var logs =
        LogCapturer.capturing(Loggers.PROCESS_PROCESSOR_LOGGER.getName(), Level.INFO)) {
      engine.forEachPartition(engine::snapshot);
      engine.stop();
      engine.start();

      Awaitility.await("pending subscriptions recovered on every partition")
          .until(
              () -> logs.messages().stream().filter(m -> m.startsWith("Recovered pending")).count(),
              count -> count == 2L * engine.getPartitionIds().size());
    }
  }
}
