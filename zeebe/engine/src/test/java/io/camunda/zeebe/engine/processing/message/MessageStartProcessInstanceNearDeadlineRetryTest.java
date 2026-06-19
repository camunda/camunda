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
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition regression for the dedup-deadline grace introduced to close the near-deadline
 * duplicate window. Once rejected asks are retried, a message can succeed at {@code T − ε} with an
 * <em>auto-completing</em> holder; a retry sent just before the message deadline {@code T} but
 * <em>processed</em> on {@code P_B} after {@code T} would, without a grace, miss the dedup row,
 * re-evaluate live state (the holder has since completed, freeing the businessId), and start a
 * <em>duplicate</em> instance.
 *
 * <p>This test drops the {@code START} reply to {@code P_K} until the clock is past the message
 * deadline but still within the grace, so the <em>first delivered</em> {@code STARTED} reply is the
 * past-deadline re-reply. With the grace configured, {@code P_B} still treats the dedup row as a
 * hit and re-replies the original process-instance key, so exactly one process instance is ever
 * created on {@code P_B}. The grace-disabled counterpart — which starts a fresh PI once the
 * deadline passes — is pinned by {@code
 * MessageStartProcessInstanceCrossPartitionHandshakeTest#shouldStartFreshPiWhenRetryArrivesAfterMessageDeadlineHasPassed}.
 */
public final class MessageStartProcessInstanceNearDeadlineRetryTest {

  private static final int PARTITION_COUNT = 3;

  // hash("ck-1") → P_K=1 and hash("biz-1") → P_B=3 under PARTITION_COUNT=3 (re-asserted in
  // @Before).
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-near-deadline";
  private static final String MESSAGE_NAME = "start-msg-near-deadline";

  private static final Duration MESSAGE_TTL = Duration.ofSeconds(5);
  private static final Duration DEDUP_GRACE = Duration.ofSeconds(10);
  private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(1);
  private static final Duration ASK_RETRY_INTERVAL = Duration.ofSeconds(1);

  /**
   * Auto-completing message-start process: the holder PI on {@code P_B} completes on its own (the
   * engine job client cannot reach a job living on {@code P_B}), so the businessId frees while the
   * dedup row is still the only thing standing between a retry and a duplicate start.
   */
  private static final BpmnModelInstance AUTO_COMPLETE_MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("autoStart")
          .message(MESSAGE_NAME)
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withInitialClusterVersionAtMax()
          .withEngineConfig(
              config ->
                  config
                      .setBusinessIdUniquenessEnabled(true)
                      .setMessageStartDedupExpirationSweepInterval(SWEEP_INTERVAL)
                      .setMessageStartAskRetryInterval(ASK_RETRY_INTERVAL)
                      .setMessageStartAskRetryGrace(DEDUP_GRACE));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as("CORRELATION_KEY and BUSINESS_ID must hash to different partitions")
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldNotDuplicatePiWhenRetryIsProcessedAfterDeadlineButWithinGrace() {
    // given an auto-completing message-start whose holder PI completes on P_B on its own
    deployAndAwaitStartEventSubscriptionsOnAllPartitions();

    // drop every START reply to P_K until we cross the message deadline, so the first STARTED that
    // P_K records is the past-deadline re-reply (dropped replies leave the pending ask alive and
    // keep the scheduler re-asking)
    final AtomicBoolean blockStartReply = new AtomicBoolean(true);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(intent == MessageStartProcessInstanceRequestIntent.START && blockStartReply.get()));

    // when the message is published; P_B creates PI_1, writes the dedup row, and PI_1
    // auto-completes
    final var publish =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .withTimeToLive(MESSAGE_TTL.toMillis())
            .publish();
    final long firstPiKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // and the clock advances past the message deadline but stays within the grace window (and well
    // within the 1-minute TTL checker interval, so the buffered message on P_K is not yet expired
    // and the ask keeps being retried)
    engine.increaseTime(MESSAGE_TTL.plusSeconds(2));

    // when the START reply is allowed through again and one more retry round completes
    blockStartReply.set(false);
    engine.increaseTime(ASK_RETRY_INTERVAL.multipliedBy(2).plusSeconds(1));

    // then the late retry succeeds and consumes the buffered message (EXPIRED on P_K is the
    // terminal of a successful handshake); bounded by that terminal, exactly one process instance
    // was ever activated on P_B and it is the original PI — the dedup grace absorbed the late
    // retry instead of letting it start a fresh, duplicate instance
    final var activations =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == MessageIntent.EXPIRED && r.getKey() == publish.getKey())
            .processInstanceRecords()
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .asList();
    assertThat(activations)
        .extracting(r -> r.getValue().getProcessInstanceKey())
        .as("the near-deadline retry must re-reply the cached PI key, not start a duplicate on P_B")
        .containsExactly(firstPiKey);
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions() {
    engine.deployment().withXmlResource(AUTO_COMPLETE_MESSAGE_START_PROCESS).deploy();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .limit(PARTITION_COUNT)
        .asList();
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
