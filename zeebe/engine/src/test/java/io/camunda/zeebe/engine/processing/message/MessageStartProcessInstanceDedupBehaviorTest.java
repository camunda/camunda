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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageStartProcessInstanceRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins the behaviour of the cross-partition message-start dedup state on {@code P_B}: the request
 * processor's short-circuit on a valid dedup hit, the {@code STARTED} applier that records the
 * dedup entry with a {@code deletionDeadline} inherited from the request's {@code messageDeadline}
 * (the originating buffered message's TTL on {@code P_K}) on a cache miss + success, and the
 * scheduled sweep that deletes past-deadline entries. The deadline is set once at insert time and
 * never refreshed; the entry exists purely to bound {@code P_K}'s retry window — there is no
 * PI-completion hook and no reverse mapping by process-instance key.
 *
 * <p>Tests run on a single-partition engine and encode the source partition in {@code messageKey}
 * as partition {@code 1}, so the cross-partition {@code START} reply is written locally and the
 * recording exporter sees it as a recorded command. No reply-side processor is registered yet
 * (lands in a later commit), so retries are driven by direct {@code REQUEST} writes.
 */
public final class MessageStartProcessInstanceDedupBehaviorTest {

  private static final String PROCESS_ID = "wf";
  private static final String MESSAGE_NAME = "start-msg";
  private static final String START_EVENT_ID = "start";
  private static final String CORRELATION_KEY = "ck";
  private static final String BUSINESS_ID = "biz-1";
  private static final long SOURCE_MESSAGE_KEY = Protocol.encodePartitionId(1, 42);
  private static final Duration MESSAGE_TTL = Duration.ofSeconds(5);
  private static final Duration SWEEP_INTERVAL = Duration.ofSeconds(1);

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent(START_EVENT_ID)
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              config ->
                  config
                      .setBusinessIdUniquenessEnabled(true)
                      .setMessageStartDedupExpirationSweepInterval(SWEEP_INTERVAL));

  @Test
  public void shouldReReplyStartedWithCachedKeyOnActiveDedupHit() {
    // given a process is deployed and a first REQUEST has produced a PI
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    writeRequest(subscriptionKey);
    final long firstPiKey = firstStartReplyPiKey();

    // when a second REQUEST for the same (processDefinitionKey, messageKey) arrives
    writeRequest(subscriptionKey);

    // then the dedup short-circuit re-replies STARTED with the original PI key and no second PI
    // is activated
    final var startReplies =
        RecordingExporter.records()
            .filter(r -> r.getRecordType() == RecordType.COMMAND)
            .filter(r -> r.getIntent() == MessageStartProcessInstanceRequestIntent.START)
            .limit(2);
    assertThat(
            startReplies
                .map(this::asRequest)
                .map(MessageStartProcessInstanceRequestRecordValue::getProcessInstanceKey))
        .as("both START replies carry the same cached PI key")
        .containsExactly(firstPiKey, firstPiKey);
    assertSinglePiActivated();
  }

  @Test
  public void shouldReReplyStartedWithCachedKeyAfterHolderPiCompletionWithinDeadline() {
    // given a PI was started via REQUEST and has since completed; the dedup entry's
    // deletionDeadline (sourced from the request's messageDeadline) has not yet passed, so the
    // entry is still present on P_B and still satisfies the dedup hit check
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    writeRequest(subscriptionKey);
    final long firstPiKey = firstStartReplyPiKey();
    completeServiceTask(firstPiKey);
    waitForRootProcessCompletion(firstPiKey);

    // when a retry arrives while the deadline is still open
    writeRequest(subscriptionKey);

    // then the second REQUEST is re-replied STARTED with the original PI key
    final var startReplies =
        RecordingExporter.records()
            .filter(r -> r.getRecordType() == RecordType.COMMAND)
            .filter(r -> r.getIntent() == MessageStartProcessInstanceRequestIntent.START)
            .limit(2);
    assertThat(
            startReplies
                .map(this::asRequest)
                .map(MessageStartProcessInstanceRequestRecordValue::getProcessInstanceKey))
        .as("dedup entry keeps re-replying the cached PI key while the deadline is open")
        .containsExactly(firstPiKey, firstPiKey);
  }

  @Test
  public void shouldRejectExpiredWhenRetryArrivesAfterDeadline() {
    // Pins the TTL-gated expiry guard (this feature): a retry that crosses the message deadline is
    // deterministically refused with REJECT_EXPIRED — it must NOT re-reply the cached key and must
    // NOT fall through to a fresh start. This closes the near-deadline duplicate window the old
    // grace period only mitigated: without the guard, a post-deadline retry that dedup-misses would
    // re-evaluate live state (the holder has completed and freed the businessId) and create a
    // duplicate.
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    final long deadline = engine.getClock().getCurrentTimeInMillis() + MESSAGE_TTL.toMillis();
    writeRequest(subscriptionKey, deadline);
    final long firstPiKey = firstStartReplyPiKey();
    completeServiceTask(firstPiKey);
    waitForRootProcessCompletion(firstPiKey);

    // advance past the message deadline
    engine.increaseTime(MESSAGE_TTL.plusSeconds(1));

    // when a retry arrives after the deadline, re-emitting the original (now past) deadline
    writeRequest(subscriptionKey, deadline);

    // then it is refused with REJECT_EXPIRED and no second START reply is ever produced
    RecordingExporter.records()
        .filter(r -> r.getRecordType() == RecordType.COMMAND)
        .withIntent(MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED)
        .getFirst();
    final long startReplies =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED)
            .filter(r -> r.getRecordType() == RecordType.COMMAND)
            .filter(r -> r.getIntent() == MessageStartProcessInstanceRequestIntent.START)
            .count();
    assertThat(startReplies)
        .as("only the first request started a PI; the post-deadline retry is expiry-rejected")
        .isEqualTo(1L);
  }

  @Test
  public void shouldRejectExpiredEvenAfterDedupEntrySwept() {
    // given a PI was started, completed, and both the message deadline and the sweep interval have
    // elapsed, so the dedup row has been swept away entirely
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    final long deadline = engine.getClock().getCurrentTimeInMillis() + MESSAGE_TTL.toMillis();
    writeRequest(subscriptionKey, deadline);
    final long firstPiKey = firstStartReplyPiKey();
    completeServiceTask(firstPiKey);
    waitForRootProcessCompletion(firstPiKey);
    engine.increaseTime(MESSAGE_TTL.plus(SWEEP_INTERVAL).plusSeconds(1));
    RecordingExporter.records()
        .withIntent(MessageStartProcessInstanceRequestIntent.EXPIRED_DEDUP_DELETED)
        .getFirst();

    // when a post-deadline retry arrives with no dedup row present, re-emitting the original
    // (now past) deadline
    writeRequest(subscriptionKey, deadline);

    // then the guard still refuses it (REJECT_EXPIRED): the guard runs before — and independently
    // of — the dedup lookup, so a swept row does not reopen the duplicate window
    RecordingExporter.records()
        .filter(r -> r.getRecordType() == RecordType.COMMAND)
        .withIntent(MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED)
        .getFirst();
    final long startReplies =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.REJECT_EXPIRED)
            .filter(r -> r.getRecordType() == RecordType.COMMAND)
            .filter(r -> r.getIntent() == MessageStartProcessInstanceRequestIntent.START)
            .count();
    assertThat(startReplies)
        .as("no fresh PI is started for an expired request, even after the dedup row is swept")
        .isEqualTo(1L);
  }

  @Test
  public void shouldTreatActiveHitWithBannedHolderAsCacheMissAndStartFreshPi() {
    // given a PI started via REQUEST, then banned
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    writeRequest(subscriptionKey);
    final long bannedPiKey = firstStartReplyPiKey();
    engine.banInstanceInNewTransaction(1, bannedPiKey);

    // when a retry of the same (processDefinitionKey, messageKey) arrives
    writeRequest(subscriptionKey);

    // then the cached entry is treated as a miss, live state evaluates clean (banned PI is
    // excluded from the uniqueness check too), and a fresh PI is created
    final long secondPiKey =
        RecordingExporter.records()
            .filter(r -> r.getRecordType() == RecordType.COMMAND)
            .filter(r -> r.getIntent() == MessageStartProcessInstanceRequestIntent.START)
            .limit(2)
            .skip(1)
            .map(this::asRequest)
            .findFirst()
            .orElseThrow()
            .getProcessInstanceKey();
    assertThat(secondPiKey).isPositive().isNotEqualTo(bannedPiKey);
  }

  @Test
  public void shouldDeleteExpiredDedupEntryViaScheduledSweep() {
    // given a PI was started, completed, the dedup entry's deletionDeadline (= the request's
    // messageDeadline) has elapsed
    engine.deployment().withXmlResource(MESSAGE_START_PROCESS).deploy();
    final long subscriptionKey = waitForStartEventSubscriptionKey();
    writeRequest(subscriptionKey);
    final long firstPiKey = firstStartReplyPiKey();
    completeServiceTask(firstPiKey);
    waitForRootProcessCompletion(firstPiKey);

    // when the deadline and one sweep interval have elapsed (the sweep now uses `now`, no grace)
    engine.increaseTime(MESSAGE_TTL.plus(SWEEP_INTERVAL).plusSeconds(1));

    // then the scheduler observes the past-deadline entry and the processor writes
    // EXPIRED_DEDUP_DELETED, whose applier removes the entry from the dedup state
    RecordingExporter.records()
        .withIntent(MessageStartProcessInstanceRequestIntent.EXPIRED_DEDUP_DELETED)
        .getFirst();
  }

  private void writeRequest(final long subscriptionKey) {
    writeRequest(
        subscriptionKey, engine.getClock().getCurrentTimeInMillis() + MESSAGE_TTL.toMillis());
  }

  private void writeRequest(final long subscriptionKey, final long messageDeadline) {
    engine.writeRecords(
        RecordToWrite.command()
            .key(subscriptionKey)
            .messageStartProcessInstanceRequest(
                MessageStartProcessInstanceRequestIntent.REQUEST,
                request(BUSINESS_ID, subscriptionKey, messageDeadline)));
  }

  private long firstStartReplyPiKey() {
    return RecordingExporter.records()
                .filter(r -> r.getRecordType() == RecordType.COMMAND)
                .filter(r -> r.getIntent() == MessageStartProcessInstanceRequestIntent.START)
                .getFirst()
                .getValue()
            instanceof MessageStartProcessInstanceRequestRecordValue v
        ? v.getProcessInstanceKey()
        : -1L;
  }

  private void completeServiceTask(final long processInstanceKey) {
    final long jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .filter(r -> r.getValue().getProcessInstanceKey() == processInstanceKey)
            .getFirst()
            .getKey();
    engine.job().withKey(jobKey).complete();
  }

  private void waitForRootProcessCompletion(final long processInstanceKey) {
    RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();
  }

  private void assertSinglePiActivated() {
    // Span the limit window across BOTH START replies so the asynchronous ELEMENT_ACTIVATING
    // event produced by the first request's ACTIVATE_ELEMENT follow-up command (which the engine
    // processes after the original REQUEST batch has committed) lands inside the window. A limit
    // anchored on the first START reply would cut the stream before that activation event is
    // exported and undercount activations.
    final long activations =
        RecordingExporter.records()
            .limitByCount(
                r ->
                    r.getRecordType() == RecordType.COMMAND
                        && r.getIntent() == MessageStartProcessInstanceRequestIntent.START,
                2)
            .processInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .count();
    assertThat(activations).isEqualTo(1L);
  }

  private MessageStartProcessInstanceRequestRecord request(
      final String businessId, final long subscriptionKey) {
    // Mirror the publisher: messageDeadline = now + ttl. A real retry re-emits this original
    // deadline verbatim; the deadline-explicit overload below is used to model that.
    return request(
        businessId,
        subscriptionKey,
        engine.getClock().getCurrentTimeInMillis() + MESSAGE_TTL.toMillis());
  }

  private MessageStartProcessInstanceRequestRecord request(
      final String businessId, final long subscriptionKey, final long messageDeadline) {
    final long processDefinitionKey =
        RecordingExporter.messageStartEventSubscriptionRecords()
            .getFirst()
            .getValue()
            .getProcessDefinitionKey();
    // The dedup row on P_B inherits messageDeadline directly, so advancing the engine clock past it
    // expires the dedup row exactly the way the buffered message on P_K would expire on a real
    // cluster.
    return new MessageStartProcessInstanceRequestRecord()
        .setMessageKey(SOURCE_MESSAGE_KEY)
        .setMessageName(BufferUtil.wrapString(MESSAGE_NAME))
        .setCorrelationKey(BufferUtil.wrapString(CORRELATION_KEY))
        .setBusinessId(BufferUtil.wrapString(businessId))
        .setProcessDefinitionKey(processDefinitionKey)
        .setBpmnProcessId(BufferUtil.wrapString(PROCESS_ID))
        .setStartEventId(BufferUtil.wrapString(START_EVENT_ID))
        .setMessageStartEventSubscriptionKey(subscriptionKey)
        .setVariables(new UnsafeBuffer())
        .setMessageDeadline(messageDeadline)
        .setMessageTtl(MESSAGE_TTL.toMillis())
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private long waitForStartEventSubscriptionKey() {
    return RecordingExporter.messageStartEventSubscriptionRecords().getFirst().getKey();
  }

  private MessageStartProcessInstanceRequestRecordValue asRequest(final Record<?> record) {
    return (MessageStartProcessInstanceRequestRecordValue) record.getValue();
  }
}
