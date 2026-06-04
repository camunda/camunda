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
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Multi-partition behavioural pin for the pull-based correlation-key lock release. A message-start
 * instance created via the cross-partition handshake runs on {@code P_B = hash(businessId)} while
 * the correlation-key lock it holds lives on {@code P_K = hash(correlationKey)}; {@code P_K} cannot
 * observe that holder completing, so it polls {@code P_B} and releases the lock when the holder is
 * gone. These tests exercise that whole loop — scheduler on {@code P_K} → query handler on {@code
 * P_B} → release reply on {@code P_K} → buffered-message pick-up — through the real inter-partition
 * transport.
 *
 * <p>Component-level behaviour that does not need the full transport is pinned with the code it
 * belongs to and is intentionally <em>not</em> re-tested here: the scheduler's back-off doubling /
 * cap and per-partition batching live in {@code
 * CrossPartitionMessageStartLockReleaseSchedulerTest}; the {@code P_B} query outcomes (active /
 * gone / banned) live in {@code MessageStartCorrelationKeyLockReleaseQueryProcessorTest}; and the
 * {@code P_K} release handler's holder-precise idempotency guard lives in {@code
 * MessageStartCorrelationKeyLockReleaseReleaseProcessorTest}. Restart / leadership recovery is a
 * property of the transient back-off bookkeeping being rebuilt from local lock state (covered by
 * the scheduler unit test's reconcile case) and needs no dedicated multi-partition pin.
 *
 * <p>The constants are chosen so {@code hash(correlationKey) != hash(businessId)}; an
 * {@code @Before} precondition fails loudly if a future hash change degenerates the scenario into a
 * single-partition path.
 */
public final class CrossPartitionMessageStartLockReleaseTest {

  private static final int PARTITION_COUNT = 3;

  // hash("ck-1") → P_K=1 and hash("biz-1") → P_B=3 under PARTITION_COUNT=3, so the holder runs on a
  // different partition than the lock. Re-asserted in @Before against hash drift.
  private static final String CORRELATION_KEY = "ck-1";
  private static final String OTHER_CORRELATION_KEY = "ck-2";
  private static final String BUSINESS_ID = "biz-1";

  private static final long LONG_TTL = Duration.ofMinutes(5).toMillis();
  private static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
  // Advanced well past a single lock's back-off so a second poll round is guaranteed to fire while
  // the holder is still active (used as a "lock survived a full round" fence).
  private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

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

  // Message-start process with a service task: the holder stays active on P_B until banned.
  private static final String SERVICE_PROCESS_ID = "wf-svc";
  private static final String SERVICE_MESSAGE_NAME = "svc-start-msg";
  private static final BpmnModelInstance SERVICE_PROCESS =
      Bpmn.createExecutableProcess(SERVICE_PROCESS_ID)
          .startEvent("svcStart")
          .message(SERVICE_MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  // None-start process used only to keep a businessId actively held by a *different* instance while
  // the original holder has already completed (the precise-release-under-reuse scenario).
  private static final String HOLD_PROCESS_ID = "wf-hold";
  private static final BpmnModelInstance HOLD_PROCESS =
      Bpmn.createExecutableProcess(HOLD_PROCESS_ID)
          .startEvent("holdStart")
          .serviceTask("holdTask", t -> t.zeebeJobType("hold"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withEngineConfig(
              config ->
                  config
                      .setBusinessIdUniquenessEnabled(true)
                      .setMessageStartLockReleasePollInterval(POLL_INTERVAL));

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
  public void shouldReleaseLockAndStartBufferedMessageWhenHolderCompletesOnPB() {
    // given a cross-partition start: the holder is created (and auto-completes) on P_B and the
    // correlation-key lock is written on P_K
    deployAndAwaitStartSubscriptions(AUTO_PROCESS, AUTO_MESSAGE_NAME);
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(AUTO_PROCESS_ID);
    awaitMessageConsumedOnPK(AUTO_MESSAGE_NAME);

    // and a second publish with the same correlation key (no businessId) is buffered behind the
    // lock
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, null);

    // and the holder completes on P_B
    awaitHolderCompleted(AUTO_PROCESS_ID);

    // when the release poll fires
    engine.increaseTime(POLL_INTERVAL.multipliedBy(2));

    // then P_K releases the lock and picks up the buffered message, starting a fresh PI on P_K
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .as("the buffered message is picked up and started on P_K after the lock is released")
        .isEqualTo(partitionFor(CORRELATION_KEY));
  }

  @Test
  public void shouldNotReleaseLockWhileHolderIsStillActiveOnPB() {
    // given a cross-partition start whose holder stays active on P_B (service task)
    deployAndAwaitStartSubscriptions(SERVICE_PROCESS, SERVICE_MESSAGE_NAME);
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(SERVICE_PROCESS_ID);
    awaitHolderJobCreated(holderKey);
    awaitMessageConsumedOnPK(SERVICE_MESSAGE_NAME);

    // and a second same-correlation-key publish buffered behind the lock
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, null);

    // when the release poll fires, then fires again past the back-off
    engine.increaseTime(POLL_INTERVAL.multipliedBy(2));
    awaitQueryRounds(1);
    engine.increaseTime(MAX_BACKOFF);

    // then a SECOND query round is observed — which can only happen if the lock still exists, i.e.
    // round 1 did not release the still-active holder. This re-query is the fence that proves the
    // lock survived a full poll round (a wrongful release would have dropped the lock, so no second
    // query would ever be sent and this await would time out).
    awaitQueryRounds(2);

    // and no RELEASE / RELEASED was ever produced for the active holder, up to the second round
    final long releases =
        RecordingExporter.records()
            .limit(nthQueried(2))
            .filter(
                r ->
                    r.getValueType() == ValueType.MESSAGE_START_CORRELATION_KEY_LOCK_RELEASE
                        && (r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.RELEASE
                            || r.getIntent()
                                == MessageStartCorrelationKeyLockReleaseIntent.RELEASED))
            .count();
    assertThat(releases).as("an active holder is never released").isZero();

    // and the buffered message stays buffered — only the holder PI was ever activated
    final long activations =
        RecordingExporter.records()
            .limit(nthQueried(2))
            .processInstanceRecords()
            .withBpmnProcessId(SERVICE_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(activations)
        .as("the buffered message stays buffered while the holder is active")
        .isEqualTo(1L);
  }

  @Test
  public void shouldReleaseLockWhenHolderIsBannedOnPB() {
    // given a cross-partition start whose holder is active on P_B, with a second message buffered
    deployAndAwaitStartSubscriptions(SERVICE_PROCESS, SERVICE_MESSAGE_NAME);
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(SERVICE_PROCESS_ID);
    awaitHolderJobCreated(holderKey);
    awaitMessageConsumedOnPK(SERVICE_MESSAGE_NAME);
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, null);

    // and the holder is banned on P_B (a stuck holder must not block its correlation key forever)
    engine.banInstanceInNewTransaction(partitionFor(BUSINESS_ID), holderKey);

    // when the release poll fires
    engine.increaseTime(POLL_INTERVAL.multipliedBy(2));

    // then the banned holder is treated as gone, the lock is released, and the buffered message
    // starts on P_K
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(SERVICE_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .as("a banned holder releases the lock and the buffered message starts on P_K")
        .isEqualTo(partitionFor(CORRELATION_KEY));
  }

  @Test
  public void shouldReleaseCompletedHolderLockEvenWhenBusinessIdWasReusedByAnotherInstance() {
    // Pins holder-instance precision: the release polls the *specific holder instance*, not
    // businessId availability, so the lock is released once the holder completes even if a
    // different instance has since taken the same businessId. A businessId-availability poll would
    // have wrongly kept the lock held here.
    deployAndAwaitStartSubscriptions(AUTO_PROCESS, AUTO_MESSAGE_NAME);
    engine.deployment().withXmlResource(HOLD_PROCESS).deploy();

    // the original holder starts on P_B via the ask and auto-completes; the lock is on P_K
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(AUTO_PROCESS_ID);
    awaitMessageConsumedOnPK(AUTO_MESSAGE_NAME);
    publishStart(AUTO_MESSAGE_NAME, CORRELATION_KEY, null);
    awaitHolderCompleted(AUTO_PROCESS_ID);

    // a *different* instance (different process) now actively holds the same businessId on P_B
    final long reuseKey =
        engine
            .processInstance()
            .ofBpmnProcessId(HOLD_PROCESS_ID)
            .onPartition(partitionFor(BUSINESS_ID))
            .withBusinessId(BUSINESS_ID)
            .create();
    awaitHolderJobCreated(reuseKey);

    // when the release poll fires — it polls the gone holder instance, not businessId availability
    engine.increaseTime(POLL_INTERVAL.multipliedBy(2));

    // then the lock is released and the buffered message starts on P_K, despite businessId still
    // being actively held by the reuse instance
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .as("a completed holder's lock is released even under businessId reuse")
        .isEqualTo(partitionFor(CORRELATION_KEY));
  }

  @Test
  public void shouldNotProactivelyRetryUniquenessBlockedMessageWhenAnUnrelatedLockIsReleased() {
    // Pins: when a correlation-key lock is released, only the buffered message for THAT key is
    // picked up. A publish rejected purely on businessId uniqueness (a *different* correlation key)
    // stays buffered and is NOT proactively retried by the release — it waits for its TTL or an
    // existing same-key trigger, exactly as on a single partition.
    deployAndAwaitStartSubscriptions(SERVICE_PROCESS, SERVICE_MESSAGE_NAME);

    // holder for correlation key A, businessId X — active on P_B, lock on P_K
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, BUSINESS_ID);
    final long holderKey = awaitHolderActivating(SERVICE_PROCESS_ID);
    awaitHolderJobCreated(holderKey);
    awaitMessageConsumedOnPK(SERVICE_MESSAGE_NAME);

    // a same-key (A) message buffered behind the lock — the release's rightful next pick-up
    publishStart(SERVICE_MESSAGE_NAME, CORRELATION_KEY, null);

    // a second publish with a *different* correlation key but the *same* businessId is rejected on
    // uniqueness and stays buffered — there is no cross-partition lock entry for it
    publishStart(SERVICE_MESSAGE_NAME, OTHER_CORRELATION_KEY, BUSINESS_ID);
    RecordingExporter.records()
        .withIntent(MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();

    // free businessId X by banning the holder, then fire the release poll (releases the A-lock)
    engine.banInstanceInNewTransaction(partitionFor(BUSINESS_ID), holderKey);
    engine.increaseTime(POLL_INTERVAL.multipliedBy(2));

    // the lock release picks up the buffered SAME-key (A) message — a fresh PI started on P_K.
    // Observing it is the deterministic fence: by now the release has been fully processed, so any
    // (wrongful) retry of the different-key blocked message would already have been emitted too.
    final var pickedUp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(SERVICE_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .filter(r -> r.getValue().getProcessInstanceKey() != holderKey)
            .getFirst();
    assertThat(Protocol.decodePartitionId(pickedUp.getValue().getProcessInstanceKey()))
        .as("the same-key buffered message is picked up on P_K when the lock is released")
        .isEqualTo(partitionFor(CORRELATION_KEY));

    // but the uniqueness-blocked different-key (B) message is NOT retried by that release: up to
    // and including the A pick-up, nothing ever correlates the B correlation key
    final long blockedKeyCorrelations =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValueType() == ValueType.PROCESS_INSTANCE
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING
                        && r.getKey() == pickedUp.getValue().getProcessInstanceKey())
            .messageStartEventSubscriptionRecords()
            .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
            .filter(r -> OTHER_CORRELATION_KEY.equals(r.getValue().getCorrelationKey()))
            .count();
    assertThat(blockedKeyCorrelations)
        .as("a uniqueness-blocked message is not proactively retried on an unrelated lock release")
        .isZero();
  }

  /**
   * Blocks until {@code rounds} holder-liveness query rounds (QUERIED events) have been exported.
   */
  private static void awaitQueryRounds(final int rounds) {
    RecordingExporter.messageStartCorrelationKeyLockReleaseRecords(
            MessageStartCorrelationKeyLockReleaseIntent.QUERIED)
        .limit(rounds)
        .asList();
  }

  /**
   * A merged-stream bound that stops at the {@code n}-th QUERIED event. Each call returns a fresh
   * stateful predicate, so it must not be shared across streams.
   */
  private static Predicate<Record<RecordValue>> nthQueried(final int n) {
    final var seen = new AtomicInteger();
    return r ->
        r.getIntent() == MessageStartCorrelationKeyLockReleaseIntent.QUERIED
            && seen.incrementAndGet() == n;
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
   * Waits for the holder's service-task {@code JOB:CREATED} — the first observable signal that the
   * PI is active and its businessId index entry is populated on {@code P_B}.
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
    RecordingExporter.messageRecords(MessageIntent.EXPIRED)
        .withName(messageName)
        .withCorrelationKey(CORRELATION_KEY)
        .getFirst();
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }
}
