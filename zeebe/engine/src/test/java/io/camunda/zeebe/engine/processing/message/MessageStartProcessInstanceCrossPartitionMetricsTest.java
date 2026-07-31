/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition metric assertions for the cross-partition message-start handshake counters M1
 * (REQUEST outcomes on {@code P_B}), M2 (reply outcomes on {@code P_K}) and M3 (asks dispatched
 * from {@code P_K}). This class drives the same scenarios as {@link
 * MessageStartProcessInstanceCrossPartitionHandshakeTest} and only asserts that the meters are
 * recorded on the expected partition registries.
 */
public final class MessageStartProcessInstanceCrossPartitionMetricsTest {

  private static final int PARTITION_COUNT = 3;

  // Same stable routing constants as the handshake test: hash("ck-1") and hash("biz-1") land on
  // different partitions so the cross-partition arm is exercised (re-asserted at @Before).
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";

  private static final String PROCESS_ID = "wf-cross";
  private static final String MESSAGE_NAME = "start-msg";
  private static final String START_EVENT_ID = "msgStart";

  private static final String REQUESTS_METRIC =
      "zeebe.message.start.cross.partition.requests.total";
  private static final String REPLIES_METRIC = "zeebe.message.start.cross.partition.replies.total";
  private static final String ASKS_METRIC = "zeebe.message.start.cross.partition.asks.total";
  private static final String ASK_DURATION_METRIC =
      "zeebe.message.start.cross.partition.asks.duration";

  private static final Duration MESSAGE_TTL = Duration.ofSeconds(5);

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  private static final BpmnModelInstance DUAL_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("noneStart")
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .moveToProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .connectTo("task")
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as(
            "CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions so the"
                + " cross-partition arm is actually exercised",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldRecordAskRequestAndReplyMetricsForCleanCrossPartitionStart() {
    // given
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);

    // when a message-start publish lands on P_K but its businessId hashes to P_B
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // and the handshake completes: the PI is started on P_B and P_K processes the STARTED reply
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(PROCESS_ID)
        .getFirst();
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.STARTED)
        .getFirst();

    // then the ask is counted on P_K (M3), the REQUEST is counted as started on P_B (M1), and the
    // STARTED reply is counted on P_K (M2)
    final int pK = partitionFor(CORRELATION_KEY);
    final int pB = partitionFor(BUSINESS_ID);
    assertThat(counter(pK, ASKS_METRIC, null, null))
        .as("M3: the cross-partition ask is dispatched from P_K")
        .isEqualTo(1.0);
    assertThat(counter(pB, REQUESTS_METRIC, "outcome", "started"))
        .as("M1: the REQUEST is decided as a clean start on P_B")
        .isEqualTo(1.0);
    assertThat(counter(pK, REPLIES_METRIC, "outcome", "started"))
        .as("M2: the STARTED reply is processed on P_K")
        .isEqualTo(1.0);
  }

  @Test
  public void shouldRecordUniquenessRejectionRequestAndReplyMetrics() {
    // given a holder PI already owns the businessId on P_B
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);
    final long holderKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .onPartition(partitionFor(BUSINESS_ID))
            .withBusinessId(BUSINESS_ID)
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(r -> r.getValue().getProcessInstanceKey() == holderKey)
        .getFirst();

    // when a message-start publish asks P_B for the same businessId
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .withTimeToLive(0L)
        .publish();

    // and P_B replies UNIQUENESS_REJECTED
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();

    // then the uniqueness rejection is counted on both the REQUEST (M1) and reply (M2) sides
    final int pK = partitionFor(CORRELATION_KEY);
    final int pB = partitionFor(BUSINESS_ID);
    assertThat(counter(pB, REQUESTS_METRIC, "outcome", "rejected_uniqueness"))
        .as("M1: the REQUEST is rejected for businessId uniqueness on P_B")
        .isGreaterThanOrEqualTo(1.0);
    assertThat(counter(pK, REPLIES_METRIC, "outcome", "rejected_uniqueness"))
        .as("M2: the uniqueness-rejection reply is processed on P_K")
        .isGreaterThanOrEqualTo(1.0);
    assertThat(counter(pK, ASKS_METRIC, null, null))
        .as("M3: at least one ask was dispatched from P_K")
        .isGreaterThanOrEqualTo(1.0);
  }

  @Test
  public void shouldRecordAskDurationAsStartedForCleanCrossPartitionStart() {
    // given
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);

    // when the cross-partition handshake completes successfully
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // and P_K processes the STARTED reply (which writes CORRELATED for the start subscription)
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CORRELATED)
        .withMessageName(MESSAGE_NAME)
        .getFirst();

    // then the ask duration is recorded once on P_K, tagged started (M7)
    final int pK = partitionFor(CORRELATION_KEY);
    assertThat(timerCount(pK, ASK_DURATION_METRIC, "outcome", "started"))
        .as("M7: the cross-partition ask duration is recorded as started on P_K")
        .isEqualTo(1L);
    assertThat(timerCount(pK, ASK_DURATION_METRIC, "outcome", "expired"))
        .as("M7: a clean start records no expired sample")
        .isZero();
  }

  @Test
  public void shouldRecordAskDurationAsExpiredWhenBlockedMessageExpiresAtTtl() {
    // given a holder PI that keeps the businessId held on P_B so every ask is rejected
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);
    final long holderKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .onPartition(partitionFor(BUSINESS_ID))
            .withBusinessId(BUSINESS_ID)
            .create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(r -> r.getValue().getProcessInstanceKey() == holderKey)
        .getFirst();

    // when a message-start publish asks P_B for the same businessId with a finite TTL
    final var blocked =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .withTimeToLive(MESSAGE_TTL.toMillis())
            .publish();
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();

    // and time advances past the messageDeadline so the buffered message expires on P_K
    engine.increaseTime(
        MESSAGE_TTL.plus(EngineConfiguration.DEFAULT_MESSAGES_TTL_CHECKER_INTERVAL));
    RecordingExporter.messageRecords(MessageIntent.EXPIRED)
        .withRecordKey(blocked.getKey())
        .getFirst();

    // then the ask duration is recorded on P_K tagged expired, never started (M7)
    final int pK = partitionFor(CORRELATION_KEY);
    assertThat(timerCount(pK, ASK_DURATION_METRIC, "outcome", "expired"))
        .as("M7: a never-freed holder lets the ask duration terminate as expired on P_K")
        .isGreaterThanOrEqualTo(1L);
    assertThat(timerCount(pK, ASK_DURATION_METRIC, "outcome", "started"))
        .as("M7: a message that never starts records no started sample")
        .isZero();
  }

  private double counter(
      final int partition, final String name, final String tagKey, final String tagValue) {
    var search = engine.getMeterRegistry(partition).find(name);
    if (tagKey != null) {
      search = search.tag(tagKey, tagValue);
    }
    final var counter = search.counter();
    return counter != null ? counter.count() : 0.0;
  }

  private long timerCount(
      final int partition, final String name, final String tagKey, final String tagValue) {
    var search = engine.getMeterRegistry(partition).find(name);
    if (tagKey != null) {
      search = search.tag(tagKey, tagValue);
    }
    final var timer = search.timer();
    return timer != null ? timer.count() : 0L;
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions(
      final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
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
