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
 * Multi-partition behavioural pin for Increment 3's cross-partition message-start handshake. The
 * unit-level coverage of the individual components (request processor outcomes, dedup hit / miss /
 * tombstone, banned-PI filter, pending-ask scheduler, reply-applier bookkeeping) lives with their
 * respective code commits; this class only asserts the end-to-end observable outcomes that exist
 * because all three pieces — routing on {@code P_K}, the {@code P_B}-side processor with dedup, and
 * the reply processors on {@code P_K} — are wired together.
 *
 * <p>The tests deliberately pick {@code correlationKey} and {@code businessId} values whose hashes
 * route them to <em>different</em> partitions so the routing flip in {@link
 * MessageCorrelateBehavior} is actually exercised; an explicit precondition check fails the test
 * loudly if a future change to the hash function silently degenerates the scenario into a
 * single-partition path.
 */
public final class MessageStartProcessInstanceCrossPartitionTest {

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
   * Process with both a none-start event and a message-start event sharing the same {@code
   * bpmnProcessId}, so that {@code CreateProcessInstance} (which always uses the none-start) and
   * the message-start handshake hit the same business-id uniqueness index. Required for the
   * cross-API tests; see plan Increment 3 commit 10.
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
    // unblock for cross-partition holders is the pull-based release path in Increment 4.

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
    // correlationKey lock written by the STARTED reply blocks the dispatch before any cross-
    // partition ask is sent
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
    // Pins the UNIQUENESS_REJECTED outcome via routing: a CreateProcessInstance holds the
    // businessId on P_B first; a later message-start publish from a *different* P_K (so the
    // routing flip dispatches the ask) is rejected. Different correlationKey is required so the
    // local correlationKey lock entry from a prior STARTED does not short-circuit the second
    // publish before the ask is dispatched — see plan Increment 3 commit 10.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);
    final long pBHolderKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .onPartition(partitionFor(BUSINESS_ID))
            .withBusinessId(BUSINESS_ID)
            .create();
    // wait for the holder's service-task JOB:CREATED on P_B — first observable signal that
    // ProcessInstanceElementActivatingV3Applier has populated the businessId index, without which
    // the subsequent ask would race the index-write applier
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .filter(r -> r.getValue().getProcessInstanceKey() == pBHolderKey)
        .getFirst();

    // when a message-start publish lands on P_K and asks P_B for the same businessId
    final var second =
        engine
            .message()
            .withName(MESSAGE_NAME)
            .withCorrelationKey(CORRELATION_KEY)
            .withBusinessId(BUSINESS_ID)
            .withTimeToLive(0L)
            .publish();

    // then P_B replies UNIQUENESS_REJECTED, the buffered message on P_K eventually expires via the
    // standard TTL path, and only the original CreateProcessInstance PI is ever activated
    RecordingExporter.records()
        .withIntent(MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED)
        .getFirst();
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
    // Cross-API α: message-start lands a PI on P_B via the cross-partition ask; a subsequent
    // CreateProcessInstance on P_B targeting the same bpmnProcessId / businessId is rejected for
    // uniqueness. Demonstrates the ADR invariant — every active root PI with a businessId lives on
    // P_B = hash(businessId) — holds across both creation paths.
    deployAndAwaitStartEventSubscriptionsOnAllPartitions(DUAL_START_PROCESS);

    engine
        .message()
        .withName(MESSAGE_NAME)
        .withCorrelationKey(CORRELATION_KEY)
        .withBusinessId(BUSINESS_ID)
        .publish();
    // ensure the holder PI's index entry exists on P_B before issuing the conflicting create
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

  private void deployAndAwaitStartEventSubscriptionsOnAllPartitions(
      final BpmnModelInstance process) {
    engine.deployment().withXmlResource(process).deploy();
    // deploy() already waits for CommandDistribution:FINISHED in multi-partition mode; we still
    // need to wait until every partition has the MessageStartEventSubscription CREATED so the
    // ask processor on P_B sees its local subscription before the first cross-partition request
    // arrives (otherwise it would reply NO_SUBSCRIPTION_REJECTED on a deployment race).
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
