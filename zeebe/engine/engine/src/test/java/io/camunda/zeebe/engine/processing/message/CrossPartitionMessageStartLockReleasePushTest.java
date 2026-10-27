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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartCorrelationKeyLockReleaseIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition behavioral pin for the <em>push</em> half of the cross-partition correlation-key
 * lock release (#57172): when the holder instance created via the handshake completes on {@code
 * P_B}, {@code P_B} pushes that completion to {@code P_K}, which releases the lock and picks up the
 * next buffered message — without the reconciliation poll being involved at all.
 *
 * <p>Unlike {@link CrossPartitionMessageStartLockReleaseTest}, whose scenarios drive the poll loop
 * with an explicit {@code increaseTime}, this class deliberately runs with the reconciliation poll
 * <em>disabled</em> (an interval far larger than any test's wall-clock runtime). The test harness's
 * {@code ControlledActorClock} follows wall-clock time by default, so a short poll interval would
 * fire on its own and race the push, making a "no QUERY" assertion flaky. Disabling the poll leaves
 * the push as the only mechanism that can release the lock: the pick-up happening at all proves the
 * push works, and the absence of any {@code QUERY} proves it was the push — not reconciliation —
 * that did it.
 *
 * <p>The constants are chosen so {@code hash(correlationKey) != hash(businessId)}; an
 * {@code @Before} precondition fails loudly if a future hash change degenerates the scenario into a
 * single-partition path.
 */
public final class CrossPartitionMessageStartLockReleasePushTest {

  private static final int PARTITION_COUNT = 3;

  // hash("ck-1") → P_K=1 and hash("biz-1") → P_B=3 under PARTITION_COUNT=3, so the holder runs on a
  // different partition than the lock. Re-asserted in @Before against hash drift.
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";

  private static final long LONG_TTL = Duration.ofMinutes(5).toMillis();

  // The reconciliation poll is disabled by making its interval far larger than any test's
  // wall-clock runtime. The harness clock follows wall-clock, so this guarantees the poll never
  // ticks during the test and the push is the sole releaser.
  private static final Duration POLL_DISABLED = Duration.ofHours(1);

  // Auto-completing message-start process: the holder completes on P_B on its own (the engine's job
  // client writes to the primary partition and could not complete a job living on P_B).
  private static final String AUTO_PROCESS_ID = "wf-auto";
  private static final String AUTO_MESSAGE_NAME = "auto-start-msg";
  private static final BpmnModelInstance AUTO_PROCESS =
      Bpmn.createExecutableProcess(AUTO_PROCESS_ID)
          .startEvent("autoStart")
          .message(AUTO_MESSAGE_NAME)
          .endEvent()
          .done();

  // Message-start process with a service task: the holder parks on a job on P_B and stays active
  // until it is cancelled, so the push can be exercised on the termination path.
  private static final String SERVICE_PROCESS_ID = "wf-svc";
  private static final String SERVICE_MESSAGE_NAME = "svc-start-msg";
  private static final BpmnModelInstance SERVICE_PROCESS =
      Bpmn.createExecutableProcess(SERVICE_PROCESS_ID)
          .startEvent("svcStart")
          .message(SERVICE_MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              config ->
                  config
                      .setBusinessIdUniquenessEnabled(true)
                      .setMessageStartLockReleasePollInterval(POLL_DISABLED));

  @Before
  public void assertCrossPartitionRouting() {
    assertThat(partitionFor(CORRELATION_KEY))
        .as(
            "CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions so the"
                + " cross-partition lock-release loop is actually exercised",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(partitionFor(BUSINESS_ID));
  }

  @Test
  public void shouldReleaseLockOnHolderCompletionWithoutPolling() {
    // given a cross-partition start: holder created (and auto-completes) on P_B, lock on P_K
    deployAndAwaitStartSubscriptions(AUTO_PROCESS, AUTO_MESSAGE_NAME);
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(AUTO_PROCESS_ID);
    awaitMessageConsumedOnPK(AUTO_MESSAGE_NAME);

    // and a second same-correlation-key publish buffered behind the lock
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, null);

    // and the holder completes on P_B
    awaitHolderCompleted(AUTO_PROCESS_ID);

    // when the reconciliation poll never fires (disabled)

    // then P_K releases the lock and starts the buffered message on P_K, driven purely by the
    // completion push from P_B
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .as("the buffered message is picked up on P_K without any poll tick")
        .isEqualTo(partitionFor(CORRELATION_KEY));

    // and not a single holder-liveness QUERY was ever sent up to that pick-up — the release was
    // push-driven, not discovered by reconciliation
    final long queries =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_INSTANCE
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                        && r.getKey() == pickedUp.getValue().getProcessInstanceKey())
            .filter(
                r ->
                    r.getValueType() == ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE
                        && r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.QUERY)
            .count();
    assertThat(queries).as("no reconciliation QUERY is needed on the push happy path").isZero();
  }

  @Test
  public void shouldEmitPushedAndDeleteHolderOriginOnHolderCompletion() {
    // given a cross-partition start: holder created (and auto-completes) on P_B, lock on P_K and a
    // holder-origin entry on P_B
    deployAndAwaitStartSubscriptions(AUTO_PROCESS, AUTO_MESSAGE_NAME);
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(AUTO_PROCESS_ID);
    awaitMessageConsumedOnPK(AUTO_MESSAGE_NAME);

    // when the holder completes on P_B (the poll is disabled, so any release is push-driven)
    awaitHolderCompleted(AUTO_PROCESS_ID);

    // then P_B emits a PUSHED event carrying the holder's lock coordinates and a request key that
    // addresses P_K
    final var pushed =
        RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(
                MessageStartCorrelationKeyLockReleaseIntent.PUSHED)
            .getFirst();
    assertThat(pushed.getPartitionId())
        .as("the completion push is emitted on the holder partition P_B")
        .isEqualTo(partitionFor(BUSINESS_ID));
    assertThat(Protocol.decodePartitionId(pushed.getValue().getRequestKey()))
        .as("the pushed request key addresses P_K")
        .isEqualTo(partitionFor(CORRELATION_KEY));
    final var holder = pushed.getValue().getHolders().get(0);
    assertThat(holder.getProcessInstanceKey()).isEqualTo(holderKey);
    assertThat(holder.getBpmnProcessId()).isEqualTo(AUTO_PROCESS_ID);
    assertThat(holder.getCorrelationKey()).isEqualTo(CORRELATION_KEY);

    // and the holder-origin entry on P_B is dropped by the PUSHED applier
    assertThat(
            engine
                .getProcessingState(partitionFor(BUSINESS_ID))
                .getMessageState()
                .getCrossPartitionStartHolderOrigin(holderKey))
        .as("the holder-origin entry is consumed by the push")
        .isNull();

    // and the RELEASE reply is delivered to P_K
    final var release =
        RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(
                MessageStartCorrelationKeyLockReleaseIntent.RELEASE)
            .getFirst();
    assertThat(release.getPartitionId())
        .as("the RELEASE is routed to the lock partition P_K")
        .isEqualTo(partitionFor(CORRELATION_KEY));

    // and the push-triggered release is counted on the holder partition P_B (M12)
    assertThat(
            engine
                .getMeterRegistry(partitionFor(BUSINESS_ID))
                .get("zeebe.message.start.cross.partition.lock.releases.sent.total")
                .tag("trigger", "push")
                .counter()
                .count())
        .as("M12: the holder completion pushes a release, counted on P_B with trigger=push")
        .isEqualTo(1.0);
  }

  @Test
  public void shouldReleaseLockOnHolderTerminationWithoutPolling() {
    // given a cross-partition start whose holder parks on a service task on P_B, lock on P_K
    deployAndAwaitStartSubscriptions(SERVICE_PROCESS, SERVICE_MESSAGE_NAME);
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(SERVICE_PROCESS_ID);
    awaitHolderJobCreated(holderKey);
    awaitMessageConsumedOnPK(SERVICE_MESSAGE_NAME);

    // and a second same-correlation-key publish buffered behind the lock
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, null);

    // when the holder is terminated (cancelled) on P_B — the poll is disabled
    engine.processInstance().withInstanceKey(holderKey).cancel();

    // then P_K releases the lock and starts the buffered message on P_K, driven by the termination
    // push from P_B
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(SERVICE_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .as("the buffered message is picked up on P_K after the holder is cancelled")
        .isEqualTo(partitionFor(CORRELATION_KEY));

    // and no reconciliation QUERY was involved
    final long queries =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_INSTANCE
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                        && r.getKey() == pickedUp.getValue().getProcessInstanceKey())
            .filter(
                r ->
                    r.getValueType() == ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE
                        && r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.QUERY)
            .count();
    assertThat(queries).as("no reconciliation QUERY is needed on the termination push").isZero();
  }

  @Test
  public void shouldNotPushForLocalMessageStartHolder() {
    // given a purely local message start (no businessId): the holder is created on P_K itself, so
    // there is no cross-partition handshake and no holder-origin entry to push from
    deployAndAwaitStartSubscriptions(AUTO_PROCESS, AUTO_MESSAGE_NAME);
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, null);
    final long holderKey = awaitHolderActivating(AUTO_PROCESS_ID);

    // and a second same-correlation-key publish buffered behind the local correlation-key lock
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, null);

    // when the local holder completes (its lock is freed by the local path, not by a push)
    awaitHolderCompleted(AUTO_PROCESS_ID);

    // then the buffered message is still picked up on P_K
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .isEqualTo(partitionFor(CORRELATION_KEY));

    // and no PUSHED event and no RELEASE were produced up to that pick-up
    final long pushOrRelease =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_INSTANCE
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                        && r.getKey() == pickedUp.getValue().getProcessInstanceKey())
            .filter(
                r ->
                    r.getValueType() == ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE
                        && (r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.PUSHED
                            || r.getIntent()
                                == MessageStartCorrelationKeyLockReleaseIntent.RELEASE))
            .count();
    assertThat(pushOrRelease)
        .as("a local message-start holder neither pushes nor releases cross-partition")
        .isZero();
  }

  @Test
  public void shouldCorrelateBufferedStreamWithoutPolling() {
    // A throughput pin for the issue's core latency claim: a backlog of same-correlation-key,
    // cross-partition starts drains end-to-end driven only by the completion push — no
    // reconciliation timer is involved at all. The poll is disabled and the clock is never
    // advanced, so if any hop depended on polling to make progress the stream would stall and this
    // test would time out. The stronger form of "~N x holder-lifetime": zero timers.
    deployAndAwaitStartSubscriptions(AUTO_PROCESS, AUTO_MESSAGE_NAME);

    final int streamLength = 5;

    // when a stream of same-correlation-key, cross-partition (businessId) starts is published, each
    // hop only after the previous hop's lock is established on P_K (its terminal EXPIRED). That
    // handshake fence is what keeps the stream deterministic without any collision: a follow-up
    // published while the previous holder is still alive buffers behind the live lock, and one
    // published after it has already completed starts cleanly on the freed businessId — it can
    // never race a second concurrent holder onto the same businessId (which would be rejected on
    // P_B and only retried by a timer the frozen clock never fires). Every hop is a cross-partition
    // holder on P_B, so every lock release can come only from a completion push.
    for (int hop = 1; hop <= streamLength; hop++) {
      publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
      awaitMessagesConsumedOnPK(AUTO_MESSAGE_NAME, hop);
    }

    // then every holder completes on P_B, purely push-driven, with the clock frozen and the poll
    // disabled
    final var completions =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .limit(streamLength)
            .asList();
    assertThat(completions)
        .as(
            "all %d buffered cross-partition starts complete without any clock advancement",
            streamLength)
        .hasSize(streamLength);
    assertThat(completions)
        .allSatisfy(
            r ->
                assertThat(Protocol.decodePartitionId(r.getValue().getProcessInstanceKey()))
                    .as("every hop is a cross-partition holder on P_B")
                    .isEqualTo(partitionFor(BUSINESS_ID)));

    // and not a single reconciliation QUERY was ever sent up to the last completion — the whole
    // stream drained on the push fast path
    final long queries =
        RecordingExporter.records()
            .limit(nthProcessCompleted(AUTO_PROCESS_ID, streamLength))
            .filter(
                r ->
                    r.getValueType() == ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE
                        && r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.QUERY)
            .count();
    assertThat(queries)
        .as("a push-driven stream needs no reconciliation poll to make progress")
        .isZero();
  }

  private void deployAndAwaitStartSubscriptions(
      final BpmnModelInstance process, final String messageName) {
    engine.deployment().withXmlResource(process).deploy();
    // deploy() waits for CommandDistribution:FINISHED; additionally wait until every partition has
    // its MessageStartEventSubscription so the P_B ask handler sees its local subscription before
    // the first cross-partition request arrives (otherwise it would reply
    // NO_SUBSCRIPTION_REJECTED).
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(messageName)
        .limit(PARTITION_COUNT)
        .asList();
  }

  private void publishStart(
      final String messageName, final String correlationKey, final String businessId) {
    var builder =
        engine
            .message()
            .withName(messageName)
            .withCorrelationKey(correlationKey)
            .withTimeToLive(LONG_TTL);
    if (businessId != null) {
      builder = builder.withBusinessId(businessId);
    }
    builder.publish();
  }

  /** First PROCESS-level activation for the given process — the holder created on {@code P_B}. */
  private static long awaitHolderActivating(final String bpmnProcessId) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withBpmnProcessId(bpmnProcessId)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst()
        .getValue()
        .getProcessInstanceKey();
  }

  private static void awaitHolderCompleted(final String bpmnProcessId) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withBpmnProcessId(bpmnProcessId)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
  }

  /**
   * Waits for the holder's service-task {@code JOB:CREATED} — proof the holder is active on P_B.
   */
  private static void awaitHolderJobCreated(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
        .getFirst();
  }

  /**
   * Waits for the buffered message to be consumed on {@code P_K} by the handshake (its terminal
   * {@code EXPIRED}), which is written in the same {@code STARTED}-reply processing that writes the
   * correlation-key lock on {@code P_K}.
   */
  private static void awaitMessageConsumedOnPK(final String messageName) {
    awaitMessagesConsumedOnPK(messageName, 1);
  }

  /**
   * Waits until {@code count} messages of the given name and correlation key have been consumed on
   * {@code P_K} by the handshake (their terminal {@code EXPIRED}). Each such {@code EXPIRED} is
   * written together with the correlation-key lock in the {@code STARTED}-reply processing, so the
   * {@code n}-th {@code EXPIRED} fences the {@code n}-th hop's lock as established — the handshake
   * fence that serialises the stream deterministically.
   */
  private static void awaitMessagesConsumedOnPK(final String messageName, final int count) {
    RecordingExporter.messageRecords(MessageIntent.EXPIRED)
        .withName(messageName)
        .withCorrelationKey(CORRELATION_KEY)
        .limit(count)
        .asList();
  }

  /**
   * A merged-stream bound that stops at the {@code n}-th PROCESS-level {@code ELEMENT_COMPLETED}
   * for the given process. Each call returns a fresh stateful predicate, so it must not be shared
   * across streams.
   */
  private static Predicate<Record<RecordValue>> nthProcessCompleted(
      final String bpmnProcessId, final int n) {
    final var seen = new AtomicInteger();
    return r -> {
      if (r.getValueType() != ValueType.PROCESS_INSTANCE
          || r.getIntent() != ProcessInstanceIntent.ELEMENT_COMPLETED) {
        return false;
      }
      final var value = (ProcessInstanceRecordValue) r.getValue();
      return value.getBpmnElementType() == BpmnElementType.PROCESS
          && bpmnProcessId.equals(value.getBpmnProcessId())
          && seen.incrementAndGet() == n;
    };
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
