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
  // A second correlation key that hashes to a partition other than P_B and other than P_K, so a
  // duplicate-businessId correlate from it is delegated to P_B and rejected on its own P_K.
  private static final String SECOND_CORRELATION_KEY = "ck-2";
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
  private static final String ROUND_TRIP_METRIC =
      "zeebe.message.start.cross.partition.asks.round.trip.duration";
  private static final String RELEASE_TO_START_METRIC =
      "zeebe.message.start.cross.partition.release.to.start.duration";

  private static final Duration MESSAGE_TTL = Duration.ofSeconds(5);
  private static final Duration ASK_RETRY_INTERVAL = Duration.ofSeconds(1);
  private static final String HOLDER_TIMER_DURATION = "PT2S";

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

  // A holder that parks on an intermediate timer (so it can be completed on a clock-controlled
  // schedule on P_B, which the engine job client cannot reach) plus a message-start arm. Used by
  // the release-to-start (M16) test: the holder blocks the first ask, its timer fires and frees the
  // businessId, and a retried ask then starts.
  private static final BpmnModelInstance TIMER_HOLDER_AND_MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("noneStart")
          .intermediateCatchEvent("holderTimer", e -> e.timerWithDuration(HOLDER_TIMER_DURATION))
          .endEvent()
          .moveToProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              config ->
                  config
                      .setBusinessIdUniquenessEnabled(true)
                      .setMessageStartAskRetryInterval(ASK_RETRY_INTERVAL));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as(
            "CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions so the"
                + " cross-partition arm is actually exercised",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(partitionFor(BUSINESS_ID));
    assertThat(partitionFor(SECOND_CORRELATION_KEY))
        .as(
            "SECOND_CORRELATION_KEY (%s) must hash to a partition other than P_B (%s) and other than"
                + " CORRELATION_KEY (%s), so the second correlate is delegated to P_B and rejected on"
                + " its own P_K",
            SECOND_CORRELATION_KEY, BUSINESS_ID, CORRELATION_KEY)
        .isNotEqualTo(partitionFor(BUSINESS_ID))
        .isNotEqualTo(partitionFor(CORRELATION_KEY));
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
    final int pK = partitionFor(CORRELATION_KEY);
    final int pB = partitionFor(BUSINESS_ID);
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(PROCESS_ID)
        .getFirst();
    RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.STARTED)
        .withPartitionId(pK)
        .getFirst();

    // then the ask is counted on P_K (M3), the REQUEST is counted as started on P_B (M1), and the
    // STARTED reply is counted on P_K (M2)
    assertThat(counter(pK, ASKS_METRIC, null, null))
        .as("M3: the cross-partition ask is dispatched from P_K")
        .isEqualTo(1.0);
    assertThat(counter(pB, REQUESTS_METRIC, "outcome", "started"))
        .as("M1: the REQUEST is decided as a clean start on P_B")
        .isEqualTo(1.0);
    assertThat(counter(pK, REPLIES_METRIC, "outcome", "started"))
        .as("M2: the STARTED reply is processed on P_K")
        .isEqualTo(1.0);
    assertThat(timerCount(pB, RELEASE_TO_START_METRIC, null, null))
        .as("M16: a clean, uncontended start was never blocked, so records no release-to-start")
        .isZero();
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

    // and the round-trip latency of the send/reply is recorded once on P_K, tagged started (M17)
    assertThat(timerCount(pK, ROUND_TRIP_METRIC, "outcome", "started"))
        .as("M17: the cross-partition ask round-trip is recorded as started on P_K")
        .isEqualTo(1L);
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

    // and each uniqueness-rejected reply recorded a round trip, while the final local expiry
    // discarded the last in-flight send unmeasured, so no round trip is tagged started (M17)
    assertThat(timerCount(pK, ROUND_TRIP_METRIC, "outcome", "rejected_uniqueness"))
        .as("M17: every uniqueness-rejected reply records a round trip on P_K")
        .isGreaterThanOrEqualTo(1L);
    assertThat(timerCount(pK, ROUND_TRIP_METRIC, "outcome", "started"))
        .as("M17: a message that never starts records no started round trip")
        .isZero();
  }

  @Test
  public void shouldRecordAskDurationAsExpiredWhenCrossPartitionCorrelateIsRejected() {
    // given a first synchronous correlate started a holder PI on P_B that owns the businessId
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);
    engine
        .messageCorrelation()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .correlate();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(PROCESS_ID)
        .await();

    // when a second synchronous correlate reuses the same businessId from a different P_K: it is
    // delegated to P_B, rejected on uniqueness, and its buffered correlate message is expired on
    // the second P_K by the deferred-response path — not by the TTL sweeper
    final var notCorrelated =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(SECOND_CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .expectNotCorrelated()
            .correlate();
    RecordingExporter.messageRecords(MessageIntent.EXPIRED)
        .withPartitionId(partitionFor(SECOND_CORRELATION_KEY))
        .withRecordKey(notCorrelated.getKey())
        .getFirst();

    // then the ask dispatched from the second P_K terminates as expired via the deferred-response
    // expiry path (M7), never as started
    final int secondPk = partitionFor(SECOND_CORRELATION_KEY);
    assertThat(timerCount(secondPk, ASK_DURATION_METRIC, "outcome", "expired"))
        .as("M7: a rejected synchronous correlate records the ask duration as expired on its P_K")
        .isGreaterThanOrEqualTo(1L);
    assertThat(timerCount(secondPk, ASK_DURATION_METRIC, "outcome", "started"))
        .as("M7: the rejected correlate never records a started sample on its P_K")
        .isZero();
  }

  @Test
  public void shouldRecordReleaseToStartWhenBlockedAskStartsAfterHolderFreesBusinessId() {
    // given a holder PI parked on a timer holds the businessId on P_B
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(TIMER_HOLDER_AND_MESSAGE_START_PROCESS);
    final long holderPiKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .onPartition(partitionFor(BUSINESS_ID))
            .withBusinessId(BUSINESS_ID)
            .create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(holderPiKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();

    // and a cross-partition message-start is blocked on the same businessId (kept and retried)
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .withTimeToLive(Duration.ofMinutes(5))
        .publish();
    RecordingExporter.records()
        .withIntent(MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();

    // when the holder's timer fires, completing it and freeing the businessId on P_B
    engine.increaseTime(Duration.ofSeconds(3));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(holderPiKey)
        .filterRootScope()
        .await();

    // and the retry scheduler re-dispatches the ask, which now starts a fresh PI on P_B
    engine.increaseTime(ASK_RETRY_INTERVAL.multipliedBy(64).plusSeconds(1));
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withElementType(BpmnElementType.PROCESS)
        .withBpmnProcessId(PROCESS_ID)
        .filter(r -> r.getValue().getProcessInstanceKey() != holderPiKey)
        .getFirst();

    // then P_B records the release-to-start latency once (M16)
    final int pB = partitionFor(BUSINESS_ID);
    assertThat(timerCount(pB, RELEASE_TO_START_METRIC, null, null))
        .as("M16: the wait from businessId release to the blocked ask starting is recorded on P_B")
        .isEqualTo(1L);
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
