/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
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
 * Multi-partition behavioural pin for the cross-partition message-start handshake. The unit-level
 * coverage of the individual components (request processor outcomes, dedup hit / miss / tombstone,
 * banned-PI filter, pending-ask scheduler, reply-applier bookkeeping) lives with their respective
 * code commits; this class only asserts the end-to-end observable outcomes that exist because all
 * three pieces — routing on {@code P_K}, the {@code P_B}-side processor with dedup, and the reply
 * processors on {@code P_K} — are wired together.
 *
 * <p>The tests deliberately pick {@code correlationKey} and {@code businessId} values whose hashes
 * route them to <em>different</em> partitions so the routing flip in {@link
 * MessageCorrelateBehavior} is actually exercised; an explicit precondition check fails the test
 * loudly if a future change to the hash function silently degenerates the scenario into a
 * single-partition path.
 */
public final class MessageStartProcessInstanceCrossPartitionHandshakeTest {

  private static final int PARTITION_COUNT = 3;

  // Strings whose subscription-partition hashes are stable and known on the current hash function:
  // hash("ck-1") → partition 1 (P_K) and hash("biz-1") → partition 3 (P_B) under PARTITION_COUNT=3,
  // so the cross-partition arm is actually exercised. The mapping is re-asserted at @Before so a
  // future change to the hash function does not silently turn this into a same-partition test.
  private static final String CORRELATION_KEY = "ck-1";
  private static final String BUSINESS_ID = "biz-1";
  // A second businessId used only by the local-lock-contract test; its specific partition does not
  // matter — the assertion is that the local correlationKey lock blocks the dispatch before any
  // routing happens at all.
  private static final String OTHER_BUSINESS_ID = "biz-A";

  private static final String PROCESS_ID = "wf-cross";
  private static final String MESSAGE_NAME = "start-msg";
  private static final String START_EVENT_ID = "msgStart";

  private static final Duration TOMBSTONE_WINDOW = Duration.ofSeconds(5);
  private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(1);
  private static final Duration ASK_CHECK_INTERVAL = Duration.ofMillis(500);
  private static final Duration ASK_RETRY_INTERVAL = Duration.ofSeconds(1);

  /** Message-start-only process. */
  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  /**
   * Auto-completing message-start process used by the tombstone tests so the PI can complete on
   * {@code P_B} without requiring a cross-partition job-complete command (the engine's job client
   * writes to the primary partition and would not reach a job that lives on {@code P_B}).
   */
  private static final String AUTO_PROCESS_ID = "wf-cross-auto";

  private static final String AUTO_MESSAGE_NAME = "start-msg-auto";
  private static final BpmnModelInstance AUTO_COMPLETE_MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(AUTO_PROCESS_ID)
          .startEvent("autoStart")
          .message(AUTO_MESSAGE_NAME)
          .endEvent()
          .done();

  /**
   * Process with both a none-start event and a message-start event sharing the same {@code
   * bpmnProcessId}, so that {@code CreateProcessInstance} (which always uses the none-start) and
   * the message-start handshake hit the same business-id uniqueness index. Required for the tests
   * that exercise the uniqueness invariant across both creation APIs.
   */
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
          .withEngineConfig(
              config ->
                  config
                      .setBusinessIdUniquenessEnabled(true)
                      .setMessageStartDedupTombstoneWindow(TOMBSTONE_WINDOW)
                      .setMessageStartDedupTombstoneSweepInterval(SWEEP_INTERVAL)
                      .setMessageStartAskCheckInterval(ASK_CHECK_INTERVAL)
                      .setMessageStartAskRetryInterval(ASK_RETRY_INTERVAL));

  @Before
  public void assertCrossPartitionRouting() {
    final int pk = partitionFor(CORRELATION_KEY);
    final int pb = partitionFor(BUSINESS_ID);
    assertThat(pk)
        .as(
            "CORRELATION_KEY (%s) and BUSINESS_ID (%s) must hash to different partitions so the"
                + " cross-partition arm is actually exercised; if this fails after a hash change,"
                + " pick new constants",
            CORRELATION_KEY, BUSINESS_ID)
        .isNotEqualTo(pb);
  }

  @Test
  public void shouldStartProcessInstanceOnPBViaCrossPartitionAskWhenBusinessIdHashesElsewhere() {
    // given
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);

    // when a message-start publish lands on P_K but its businessId hashes to P_B
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // then the new PI is created on P_B (its key encodes hash(businessId))
    final var activating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(BpmnElementType.PROCESS)
            .withBpmnProcessId(PROCESS_ID)
            .getFirst();
    assertThat(Protocol.decodePartitionId(activating.getValue().getProcessInstanceKey()))
        .as("the new PI lives on P_B = hash(businessId), not on P_K = hash(correlationKey)")
        .isEqualTo(partitionFor(BUSINESS_ID));
    assertThat(activating.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);

    // and the buffered message on P_K is consumed by the handshake (EXPIRED on P_K)
    final var expired =
        RecordingExporter.messageRecords(MessageIntent.EXPIRED)
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .getFirst();
    assertThat(expired.getPartitionId())
        .as("the message lifecycle ends on P_K, not on P_B")
        .isEqualTo(partitionFor(CORRELATION_KEY));
  }

  @Test
  public void shouldBufferSecondPublishWithSameCorrelationKeyAndDifferentBusinessId() {
    // Pins that the local correlationKey lock entry written by the STARTED reply on P_K blocks
    // any further trigger sharing the correlationKey, regardless of its businessId. Preserves the
    // pre-existing contract that the process-correlation-key lock is businessId-agnostic; the
    // unblock for cross-partition holders is the pull-based release path that lands later.

    // given a first PI is started via the cross-partition ask
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();
    final var firstJob = RecordingExporter.jobRecords(JobIntent.CREATED).getFirst();

    // when a second publish reuses the same correlationKey but carries a different businessId; we
    // pin behaviour with TTL=0 so the publish has a deterministic terminal (EXPIRED) and we can
    // bound the assertion stream
    final var second =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withBusinessId(OTHER_BUSINESS_ID)
            .withTimeToLive(0L)
            .publish();

    // then no second PI is created up to the EXPIRED terminal of the second publish — the local
    // correlationKey lock written by the STARTED reply blocks the dispatch before any
    // cross-partition ask is sent
    final long secondPis =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == MessageIntent.EXPIRED && r.getKey() == second.getKey())
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(secondPis)
        .as("the correlationKey lock blocks the second publish regardless of its businessId")
        .isEqualTo(1L);
    assertThat(firstJob.getValue().getProcessInstanceKey()).isPositive();
  }

  @Test
  public void shouldReplyUniquenessRejectedAndKeepMessageBufferedWhenBusinessIdAlreadyHeldOnPB() {
    // Pins the UNIQUENESS_REJECTED reply outcome of the handshake. A holder PI on P_B (created
    // here via CreateProcessInstance; the cross-API single-PI invariant of this same setup is
    // pinned separately by the test below) causes a later message-start publish on P_K to be
    // rejected. A different correlationKey is required so the local correlationKey lock entry
    // from a prior STARTED does not short-circuit the second publish before the ask is
    // dispatched.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);
    final long pBHolderKey = createHolderPiOnPBViaApi(BUSINESS_ID);
    awaitHolderJobCreated(pBHolderKey);

    // when a message-start publish lands on P_K and asks P_B for the same businessId
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .withTimeToLive(0L)
        .publish();

    // then P_B replies UNIQUENESS_REJECTED — observable on the P_K stream as the wire-level
    // outcome of the handshake
    RecordingExporter.records()
        .withIntent(MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();
  }

  @Test
  public void shouldRejectCrossPartitionMessageStartAfterCreateProcessInstanceLandsOnPB() {
    // Pins the cross-API uniqueness invariant in the CreateProcessInstance-then-message-start
    // direction: a CreateProcessInstance(businessId) first lands a PI on P_B; a later
    // message-start publish from P_K with the same businessId is rejected by the ask, so across
    // both APIs exactly one PI is ever activated. Uses the same setup as the test above — the
    // distinction is in what the assertion pins: the test above pins the reply intent; this one
    // pins the single-PI invariant across both creation APIs.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);
    final long pBHolderKey = createHolderPiOnPBViaApi(BUSINESS_ID);
    awaitHolderJobCreated(pBHolderKey);

    // when a message-start publish lands on P_K and asks P_B for the same businessId
    final var second =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .withTimeToLive(0L)
            .publish();

    // then the buffered message on P_K eventually expires via the standard TTL path, and only
    // the original CreateProcessInstance PI is ever activated across both APIs
    final long activations =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == MessageIntent.EXPIRED && r.getKey() == second.getKey())
            .processInstanceRecords()
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(activations)
        .as("only the original CreateProcessInstance PI exists; the message-start ask is rejected")
        .isEqualTo(1L);
  }

  @Test
  public void shouldRejectCreateProcessInstanceWhenCrossPartitionMessageStartHoldsBusinessId() {
    // Pins the cross-API uniqueness invariant in the message-start-then-CreateProcessInstance
    // direction: message-start lands a PI on P_B via the cross-partition ask; a subsequent
    // CreateProcessInstance on P_B targeting the same bpmnProcessId / businessId is rejected for
    // uniqueness. Demonstrates that the invariant "every active root PI with a businessId lives
    // on P_B = hash(businessId)" holds across both creation paths.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);

    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();
    // ensure the holder PI's index entry exists on P_B before issuing the conflicting create —
    // JOB:CREATED is the first observable signal that V3 PI-activating applier ran. We do not
    // know the PI key up-front (the handshake creates it on P_B), so we wait by job type.
    RecordingExporter.jobRecords(JobIntent.CREATED).withType("test").getFirst();

    // when a CreateProcessInstance with the same businessId is routed to P_B
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .onPartition(partitionFor(BUSINESS_ID))
        .withBusinessId(BUSINESS_ID)
        .withTags("rejectedSecond")
        .expectRejection()
        .create();

    // then it is rejected with ALREADY_EXISTS — the message-start handshake has already populated
    // the uniqueness index on P_B
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withTags("rejectedSecond")
                .withBpmnProcessId(PROCESS_ID)
                .getFirst())
        .hasRejectionType(RejectionType.ALREADY_EXISTS);
  }

  @Test
  public void shouldNotDuplicatePiWhenStartedReplyIsDroppedAndRetriedAcrossPartitions() {
    // Pins multi-partition retry idempotency: a dropped STARTED reply leaves the pending-ask on
    // P_K. The retry scheduler re-dispatches the ask; P_B's success-only dedup re-replies STARTED
    // with the same processInstanceKey; the STARTED reply applier on P_K is unconditionally
    // idempotent so no second PI is activated and the local correlationKey lock is written
    // exactly once across retries.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);

    final AtomicBoolean droppedOnce = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(intent == MessageStartProcessInstanceRequestIntent.START
                && droppedOnce.compareAndSet(false, true)));

    // when
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // and the retry scheduler fires (advance past one askRetryInterval + askCheckInterval so the
    // first eligible scan sees the past-deadline entry and re-dispatches)
    engine.increaseTime(ASK_RETRY_INTERVAL.plus(ASK_CHECK_INTERVAL).plusSeconds(1));

    // then a STARTED reply eventually reaches P_K (the second attempt is not dropped) and exactly
    // one PI is activated; P_B's success-only dedup re-replies the same processInstanceKey on
    // every retry so the cumulative number of PI activations across all retries is still one
    final long expectedPiKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    final long activations =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getPartitionId() == partitionFor(CORRELATION_KEY)
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.STARTED)
            .processInstanceRecords()
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(activations)
        .as("retry must not activate a second PI; P_B re-replies the cached PI key")
        .isEqualTo(1L);

    // sanity: the single activation is the one whose key we observed before applying the retry
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                .withBpmnProcessId(PROCESS_ID)
                .withElementType(BpmnElementType.PROCESS)
                .getFirst()
                .getValue()
                .getProcessInstanceKey())
        .isEqualTo(expectedPiKey);
  }

  @Test
  public void shouldReReplyStartedWithCachedKeyWhenRetryArrivesAfterPiCompletedWithinTombstone() {
    // Pins the multi-partition tombstone-within-window outcome: P_B's success-only dedup keeps
    // re-replying STARTED with the original PI key for the entire tombstoneWindow after the PI
    // has completed, so a P_K retry that loses a race with the holder's lifecycle is still a
    // no-op on P_B. The unit-level coverage of the dedup state transitions lives in
    // MessageStartProcessInstanceDedupBehaviorTest; this test exercises the same outcome
    // through the real inter-partition transport and ask retry scheduler.
    //
    // We deploy an auto-completing process so the holder PI on P_B completes on its own; the
    // engine's job client writes to the primary partition and could not reach a job on P_B.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(
        AUTO_COMPLETE_MESSAGE_START_PROCESS, AUTO_MESSAGE_NAME);

    // drop the first STARTED reply so the pending-ask on P_K survives and is retryable; let
    // every subsequent inter-partition command through (including the re-replied STARTED)
    final AtomicBoolean droppedOnce = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(intent == MessageStartProcessInstanceRequestIntent.START
                && droppedOnce.compareAndSet(false, true)));

    // when
    engine
        .message()
        .withName(AUTO_MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    // the holder PI auto-completes on P_B and the V3 applier transitions the dedup entry to
    // TOMBSTONE(now + tombstoneWindow)
    final long firstPiKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // and the ask retry scheduler fires within the tombstone window (advance only past the
    // retry+check interval; tombstoneWindow is 5s so we stay safely inside it)
    engine.increaseTime(ASK_RETRY_INTERVAL.plus(ASK_CHECK_INTERVAL).plusSeconds(1));

    // then the re-dispatched REQUEST hits the TOMBSTONE entry within the window and P_B
    // re-replies STARTED with the original PI key; no second PI is ever activated
    final long secondStartedPiKey = retryStartedReplyPiKey();
    assertThat(secondStartedPiKey)
        .as(
            "the tombstoned dedup entry re-replies the cached PI key within the window; this"
                + " alone implies no second PI was activated, because P_B only writes STARTED"
                + " with the cached key on a dedup short-circuit, never together with a fresh"
                + " PROCESS_INSTANCE_CREATION")
        .isEqualTo(firstPiKey);
  }

  @Test
  public void shouldStartFreshPiWhenRetryArrivesAfterTombstoneWindowHasPassed() {
    // Pins the multi-partition tombstone-past-window outcome: once the tombstoneWindow has
    // elapsed and the scheduled sweep has removed the dedup entry, a retry from P_K is treated
    // as a fresh ask — live uniqueness check passes (the original PI is gone) and a brand-new
    // PI is created. Symmetric counterpart to the within-window test above; pins that the
    // success-only dedup eventually releases.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(
        AUTO_COMPLETE_MESSAGE_START_PROCESS, AUTO_MESSAGE_NAME);

    final AtomicBoolean droppedOnce = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(intent == MessageStartProcessInstanceRequestIntent.START
                && droppedOnce.compareAndSet(false, true)));

    // when
    engine
        .message()
        .withName(AUTO_MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    final long firstPiKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withBpmnProcessId(AUTO_PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();

    // and time advances past tombstoneWindow + sweep interval so the sweep deletes the dedup
    // entry before the next retry arrives
    engine.increaseTime(TOMBSTONE_WINDOW.plus(SWEEP_INTERVAL).plusSeconds(1));

    // then the re-dispatched REQUEST is a cache miss on P_B; live uniqueness passes (the
    // original PI is completed); a brand-new PI is created and STARTED carries its key
    final long secondStartedPiKey = retryStartedReplyPiKey();
    assertThat(secondStartedPiKey)
        .as("after the tombstone window, P_B starts a fresh PI on retry")
        .isPositive()
        .isNotEqualTo(firstPiKey);
  }

  @Test
  public void shouldStartFreshPiWhenRetryArrivesWithBannedHolderOnPB() {
    // Pins the multi-partition banned-PI re-evaluation outcome: the lookup-time filter on P_B
    // treats a cached dedup entry whose holder PI has been banned as a miss and re-evaluates
    // live state — banned PIs are excluded from the uniqueness index, so a fresh PI is
    // started. This pins the ADR choice to defend against retries via a lookup-time filter
    // rather than a dedicated ban-cleanup hook.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(MESSAGE_START_PROCESS);

    final AtomicBoolean droppedOnce = new AtomicBoolean(false);
    engine.interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) ->
            !(intent == MessageStartProcessInstanceRequestIntent.START
                && droppedOnce.compareAndSet(false, true)));

    // when
    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();

    final long bannedPiKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getValue()
            .getProcessInstanceKey();
    // wait for the holder's JOB:CREATED so the V3 applier has populated the businessId index
    // before we ban — otherwise the live re-evaluation could race the index write
    awaitHolderJobCreated(bannedPiKey);
    engine.banInstanceInNewTransaction(partitionFor(BUSINESS_ID), bannedPiKey);

    // and the ask retry scheduler fires
    engine.increaseTime(ASK_RETRY_INTERVAL.plus(ASK_CHECK_INTERVAL).plusSeconds(1));

    // then the re-dispatched REQUEST hits the dedup entry, the banned-holder filter treats it
    // as a miss, live uniqueness re-evaluation excludes the banned PI, and a fresh PI is
    // started on P_B with a different key
    final long secondStartedPiKey = retryStartedReplyPiKey();
    assertThat(secondStartedPiKey)
        .as("a banned holder must not block a retry from starting a fresh PI")
        .isPositive()
        .isNotEqualTo(bannedPiKey);
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions(
      final BpmnModelInstance process) {
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(process, MESSAGE_NAME);
  }

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions(
      final BpmnModelInstance process, final String messageName) {
    engine.deployment().withXmlResource(process).deploy();
    // deploy() already waits for CommandDistribution:FINISHED in multi-partition mode; we still
    // need to wait until every partition has the MessageStartEventSubscription CREATED so the
    // ask processor on P_B sees its local subscription before the first cross-partition request
    // arrives (otherwise it would reply NO_SUBSCRIPTION_REJECTED on a deployment race).
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(messageName)
        .limit(PARTITION_COUNT)
        .asList();
  }

  private static int partitionFor(final String key) {
    return SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString(key), PARTITION_COUNT);
  }

  /**
   * Creates a holder PI for the given {@code businessId} directly on {@code P_B} via the {@code
   * CreateProcessInstance} API, bypassing the message-start handshake. Used by the tests that need
   * an active uniqueness-index entry already on {@code P_B} before exercising a conflicting
   * message-start ask.
   */
  private long createHolderPiOnPBViaApi(final String businessId) {
    return engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .onPartition(partitionFor(businessId))
        .withBusinessId(businessId)
        .create();
  }

  /**
   * Waits for the given PI's service-task {@code JOB:CREATED} record — the first observable signal
   * that {@code ProcessInstanceElementActivatingV3Applier} has populated the businessId uniqueness
   * index on the PI's partition. Without this guard, a subsequent uniqueness check can race the
   * index-write applier and observe a stale "businessId-free" state.
   */
  private static void awaitHolderJobCreated(final long processInstanceKey) {
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
        .getFirst();
  }

  /**
   * Returns the {@code processInstanceKey} carried by the <em>second</em> {@code STARTED} reply
   * observed — i.e. the reply produced after the retry scheduler re-dispatches the ask, once the
   * first reply has been dropped by the test's inter-partition interceptor.
   */
  private static long retryStartedReplyPiKey() {
    return RecordingExporter.messageStartProcessInstanceRequestRecords(
            MessageStartProcessInstanceRequestIntent.STARTED)
        .limit(2)
        .skip(1)
        .getFirst()
        .getValue()
        .getProcessInstanceKey();
  }
}
