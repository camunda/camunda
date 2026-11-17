/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.BlockReason;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReleaseResult;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReleaseTrigger;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReplyOutcome;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.RequestOutcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Recorder-level unit tests: verifies that every recording method registers the documented meter
 * under the expected name and tags and increments/records it. The per-partition wiring and the
 * behavioural call sites are covered by the processor, scheduler and multi-partition tests added in
 * the following commits.
 */
final class MessageCorrelationMetricsTest {

  private static final String ASK_DURATION_METRIC =
      "zeebe.message.start.cross.partition.asks.duration";
  private static final String ROUND_TRIP_METRIC =
      "zeebe.message.start.cross.partition.asks.round.trip.duration";
  private static final String RELEASE_TO_START_METRIC =
      "zeebe.message.start.cross.partition.release.to.start.duration";

  private static final String PROCESS_ID = "process-1";
  private static final String TENANT = "tenant-1";

  private SimpleMeterRegistry registry;
  private MessageCorrelationMetrics metrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    metrics = new MessageCorrelationMetrics(registry);
  }

  @Test
  void shouldRecordRequestOutcomesTaggedByOutcome() {
    // when
    metrics.crossPartitionRequest(RequestOutcome.STARTED);
    metrics.crossPartitionRequest(RequestOutcome.STARTED);
    metrics.crossPartitionRequest(RequestOutcome.REJECTED_UNIQUENESS);

    // then each outcome is counted independently under its own tag (M1)
    assertThat(requestCount("started")).isEqualTo(2.0);
    assertThat(requestCount("rejected_uniqueness")).isEqualTo(1.0);
    assertThat(requestCount("dedup_hit")).isZero();
  }

  @Test
  void shouldRecordReplyOutcomesTaggedByOutcome() {
    // when
    metrics.crossPartitionReply(ReplyOutcome.STARTED);
    metrics.crossPartitionReply(ReplyOutcome.REJECTED_NO_SUBSCRIPTION);

    // then (M2)
    assertThat(replyCount("started")).isEqualTo(1.0);
    assertThat(replyCount("rejected_no_subscription")).isEqualTo(1.0);
  }

  @Test
  void shouldCountAsksSent() {
    // when
    metrics.crossPartitionAskSent();
    metrics.crossPartitionAskSent();

    // then (M3)
    assertThat(counter("zeebe.message.start.cross.partition.asks.total")).isEqualTo(2.0);
  }

  @Test
  void shouldCountAskRetries() {
    // when
    metrics.crossPartitionAskRetried();

    // then (M8)
    assertThat(counter("zeebe.message.start.cross.partition.asks.retries.total")).isEqualTo(1.0);
  }

  @Test
  void shouldCountLockReleaseQueries() {
    // when
    metrics.lockReleaseQuerySent();
    metrics.lockReleaseQuerySent();
    metrics.lockReleaseQuerySent();

    // then (M9)
    assertThat(counter("zeebe.message.start.cross.partition.lock.release.queries.total"))
        .isEqualTo(3.0);
  }

  @Test
  void shouldRecordLockReleaseQueryBatchSizeDistribution() {
    // when
    metrics.lockReleaseQueryBatchSize(4);
    metrics.lockReleaseQueryBatchSize(2);

    // then (M10)
    final var summary =
        registry.get("zeebe.message.start.cross.partition.lock.release.query.batch.size").summary();
    assertThat(summary.count()).isEqualTo(2L);
    assertThat(summary.totalAmount()).isEqualTo(6.0);
    assertThat(summary.max()).isEqualTo(4.0);
  }

  @Test
  void shouldCountSweptDedupEntriesByBatchAmount() {
    // when
    metrics.dedupSwept(5);
    metrics.dedupSwept(3);

    // then the swept counter accumulates the swept amounts, not the number of calls (M11)
    assertThat(counter("zeebe.message.start.cross.partition.dedup.swept.total")).isEqualTo(8.0);
  }

  @Test
  void shouldRecordLockReleasesSentTaggedByTrigger() {
    // when
    metrics.lockReleaseSent(ReleaseTrigger.PUSH);
    metrics.lockReleaseSent(ReleaseTrigger.RECONCILIATION);
    metrics.lockReleaseSent(ReleaseTrigger.RECONCILIATION);

    // then (M12)
    assertThat(lockReleaseSentCount("push")).isEqualTo(1.0);
    assertThat(lockReleaseSentCount("reconciliation")).isEqualTo(2.0);
  }

  @Test
  void shouldRecordLockReleasesAppliedTaggedByResult() {
    // when
    metrics.lockReleased(ReleaseResult.RELEASED);
    metrics.lockReleased(ReleaseResult.REDUNDANT);

    // then (M13)
    assertThat(lockReleaseCount("released")).isEqualTo(1.0);
    assertThat(lockReleaseCount("redundant")).isEqualTo(1.0);
  }

  @Test
  void shouldRecordBlockedStartsTaggedByReason() {
    // when
    metrics.messageStartBlocked(BlockReason.CORRELATION_KEY);
    metrics.messageStartBlocked(BlockReason.BUSINESS_ID);
    metrics.messageStartBlocked(BlockReason.BUSINESS_ID);

    // then each reason is counted independently under its own tag (M14)
    assertThat(blockedCount("correlation_key")).isEqualTo(1.0);
    assertThat(blockedCount("business_id")).isEqualTo(2.0);
  }

  @Test
  void shouldRecordAskDurationAsStarted() {
    // given a dispatched cross-partition ask
    metrics.startCrossPartitionAsk(1L, 100L);

    // when the STARTED reply lands on P_K
    metrics.completeCrossPartitionAskStarted(1L, 100L);

    // then the ask duration is recorded under outcome=started (M7)
    final var timer = registry.find(ASK_DURATION_METRIC).tag("outcome", "started").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  void shouldRecordAskDurationAsExpiredForEveryPendingProcessDefinition() {
    // given a single message fanned out to two process definitions
    metrics.startCrossPartitionAsk(1L, 100L);
    metrics.startCrossPartitionAsk(1L, 200L);

    // when the buffered message expires
    metrics.expireCrossPartitionAsks(1L);

    // then both outstanding samples are recorded under outcome=expired (M7)
    final var timer = registry.find(ASK_DURATION_METRIC).tag("outcome", "expired").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(2L);
  }

  @Test
  void shouldNotRecordAskDurationWhenNoSampleIsTracked() {
    // when a terminal fires for an ask that was never started (e.g. after a leader change)
    metrics.completeCrossPartitionAskStarted(1L, 100L);
    metrics.expireCrossPartitionAsks(2L);

    // then no ask-duration timer is ever registered (M7)
    assertThat(registry.find(ASK_DURATION_METRIC).timers()).isEmpty();
  }

  @Test
  void shouldStartAskDurationOnlyOncePerMessageAndProcessDefinition() {
    // given a dispatched ask that is re-dispatched (retried) before any terminal
    metrics.startCrossPartitionAsk(1L, 100L);
    metrics.startCrossPartitionAsk(1L, 100L);

    // when the STARTED reply lands once
    metrics.completeCrossPartitionAskStarted(1L, 100L);

    // then exactly one duration is recorded and nothing is left to expire (M7)
    assertThat(registry.get(ASK_DURATION_METRIC).tag("outcome", "started").timer().count())
        .isEqualTo(1L);
    metrics.expireCrossPartitionAsks(1L);
    assertThat(registry.find(ASK_DURATION_METRIC).tag("outcome", "expired").timer()).isNull();
  }

  @Test
  void shouldClearPendingAskSamplesOnRecovery() {
    // given a dispatched ask
    metrics.startCrossPartitionAsk(1L, 100L);

    // when recovery drops the in-memory samples of the previous leadership term
    metrics.onRecovered(null);

    // then a subsequent terminal is a no-op — the stale sample is gone (M7)
    metrics.completeCrossPartitionAskStarted(1L, 100L);
    assertThat(registry.find(ASK_DURATION_METRIC).timers()).isEmpty();
  }

  @Test
  void shouldRecordRoundTripTaggedByReplyOutcome() {
    // given an ask has been sent
    metrics.startRoundTrip(1L, 100L);

    // when its reply lands on P_K
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);

    // then the round trip is recorded under the reply outcome tag (M17)
    final var timer = registry.find(ROUND_TRIP_METRIC).tag("outcome", "started").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  void shouldRecordEachSendReplyAttemptOfARepeatedlyRejectedAsk() {
    // given an ask that is rejected on uniqueness, retried, and finally started
    metrics.startRoundTrip(1L, 100L);
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.REJECTED_UNIQUENESS);
    metrics.startRoundTrip(1L, 100L);
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);

    // then every send/reply attempt contributes one sample under its own outcome (M17)
    assertThat(
            registry.get(ROUND_TRIP_METRIC).tag("outcome", "rejected_uniqueness").timer().count())
        .isEqualTo(1L);
    assertThat(registry.get(ROUND_TRIP_METRIC).tag("outcome", "started").timer().count())
        .isEqualTo(1L);
  }

  @Test
  void shouldMeasureRoundTripAgainstTheLastSendWhenRetriedBeforeAReply() {
    // given an ask is (re)sent twice — a retry supersedes the first send — before any reply
    metrics.startRoundTrip(1L, 100L);
    metrics.startRoundTrip(1L, 100L);

    // when a single reply lands
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);

    // then exactly one sample is recorded (last-send-wins) and nothing is left to record (M17)
    assertThat(registry.get(ROUND_TRIP_METRIC).tag("outcome", "started").timer().count())
        .isEqualTo(1L);
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);
    assertThat(registry.get(ROUND_TRIP_METRIC).tag("outcome", "started").timer().count())
        .isEqualTo(1L);
  }

  @Test
  void shouldNotRecordRoundTripWhenNoSampleIsTracked() {
    // when a reply is processed for an ask whose send was never tracked (e.g. after a leader
    // change)
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);

    // then no round-trip timer is ever registered (M17)
    assertThat(registry.find(ROUND_TRIP_METRIC).timers()).isEmpty();
  }

  @Test
  void shouldDiscardRoundTripOnLocalExpiryWithoutRecording() {
    // given a message fanned out to two process definitions, each dispatched — at dispatch an ask
    // holds both an ask-duration (M7) and a round-trip (M17) sample
    metrics.startCrossPartitionAsk(1L, 100L);
    metrics.startRoundTrip(1L, 100L);
    metrics.startCrossPartitionAsk(1L, 200L);
    metrics.startRoundTrip(1L, 200L);

    // when the buffered message expires locally with no reply
    metrics.expireCrossPartitionAsks(1L);

    // then no round trip is recorded — an incomplete round trip is not a latency (M17)
    assertThat(registry.find(ROUND_TRIP_METRIC).timers()).isEmpty();
    // and the samples are gone: a late reply after expiry is a no-op
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);
    metrics.stopRoundTrip(1L, 200L, ReplyOutcome.STARTED);
    assertThat(registry.find(ROUND_TRIP_METRIC).timers()).isEmpty();
  }

  @Test
  void shouldDiscardOnlyTheExpiredMessageRoundTrips() {
    // given dispatched asks for two different messages (each with paired M7 + M17 samples)
    metrics.startCrossPartitionAsk(1L, 100L);
    metrics.startRoundTrip(1L, 100L);
    metrics.startCrossPartitionAsk(2L, 100L);
    metrics.startRoundTrip(2L, 100L);

    // when only the first message expires locally
    metrics.expireCrossPartitionAsks(1L);

    // then the other message's reply is still measured (M17)
    metrics.stopRoundTrip(2L, 100L, ReplyOutcome.STARTED);
    assertThat(registry.get(ROUND_TRIP_METRIC).tag("outcome", "started").timer().count())
        .isEqualTo(1L);
  }

  @Test
  void shouldDiscardRoundTripOnExpiryEvenWithoutAPairedAskDurationSample() {
    // given a round-trip sample re-armed by a scheduler retry that has no matching ask-duration
    // (M7) sample — the ask-duration sample is only ever created at the original dispatch, so after
    // a leader change replays the pending-ask state the retry recreates only the round-trip (M17)
    metrics.startRoundTrip(1L, 100L);

    // when the buffered message expires locally with no reply
    metrics.expireCrossPartitionAsks(1L);

    // then the orphaned round-trip sample is still discarded — the discard is keyed off the message
    // alone, independent of the M7 map (M17)
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);
    assertThat(registry.find(ROUND_TRIP_METRIC).timers()).isEmpty();
  }

  @Test
  void shouldRecordRoundTripPerProcessDefinitionOfTheSameMessage() {
    // given a single message fanned out to two process definitions, each sent
    metrics.startRoundTrip(1L, 100L);
    metrics.startRoundTrip(1L, 200L);

    // when each receives its own reply
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);
    metrics.stopRoundTrip(1L, 200L, ReplyOutcome.REJECTED_UNIQUENESS);

    // then both are measured independently under their own outcomes (M17)
    assertThat(registry.get(ROUND_TRIP_METRIC).tag("outcome", "started").timer().count())
        .isEqualTo(1L);
    assertThat(
            registry.get(ROUND_TRIP_METRIC).tag("outcome", "rejected_uniqueness").timer().count())
        .isEqualTo(1L);
  }

  @Test
  void shouldClearRoundTripSamplesOnRecovery() {
    // given an outstanding send
    metrics.startRoundTrip(1L, 100L);

    // when recovery drops the in-memory samples of the previous leadership term
    metrics.onRecovered(null);

    // then a subsequent reply is a no-op — the stale sample is gone (M17)
    metrics.stopRoundTrip(1L, 100L, ReplyOutcome.STARTED);
    assertThat(registry.find(ROUND_TRIP_METRIC).timers()).isEmpty();
  }

  @Test
  void shouldRecordReleaseToStartFromFreeToStart() {
    // given a cross-partition ask (messageKey 100) is blocked on a held businessId
    blockAsk("biz-1", 100L);
    // and its holder frees the businessId at t=1000
    freeBusinessId("biz-1", 1_000L);

    // when the blocked ask starts at t=1250
    releaseToStart("biz-1", 100L, 1_250L);

    // then the release-to-start latency is recorded once as 250ms (M16)
    final var timer = registry.get(RELEASE_TO_START_METRIC).timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(250.0);
  }

  @Test
  void shouldNotRecordReleaseToStartWhenBusinessIdWasNotFreed() {
    // given a blocked ask whose holder never reported a free (e.g. banned/migrated)
    blockAsk("biz-1", 100L);

    // when it starts anyway
    releaseToStart("biz-1", 100L, 1_250L);

    // then nothing is recorded — there is no release time to measure from (M16)
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().count()).isZero();
  }

  @Test
  void shouldNotRecordReleaseToStartForUncontendedCompletionAndReuse() {
    // given a holder frees a businessId that no ask was ever blocked on (uncontended)
    freeBusinessId("biz-1", 1_000L);

    // when a fresh, never-blocked start later reuses that businessId
    releaseToStart("biz-1", 100L, 9_999_000L);

    // then it is not measured — a benign reuse gap must not pollute the histogram (M16)
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().count()).isZero();
  }

  @Test
  void shouldMeasureOnlyTheBlockedAskNotAnUncontendedStartOnTheSameBusinessId() {
    // given ask 100 is blocked on a businessId that is then freed
    blockAsk("biz-1", 100L);
    freeBusinessId("biz-1", 1_000L);

    // when a different, never-blocked ask 200 starts on the same businessId first
    releaseToStart("biz-1", 200L, 1_100L);

    // then it is ignored, and only the genuinely blocked ask 100 is measured when it starts (M16)
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().count()).isZero();
    releaseToStart("biz-1", 100L, 1_300L);
    final var timer = registry.get(RELEASE_TO_START_METRIC).timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(300.0);
  }

  @Test
  void shouldConsumeBlockedAskSoASecondStartDoesNotRecordAgain() {
    // given a blocked ask already measured by its start
    blockAsk("biz-1", 100L);
    freeBusinessId("biz-1", 1_000L);
    releaseToStart("biz-1", 100L, 1_250L);

    // when a second start (e.g. a dedup-hit retry) fires for the same ask
    releaseToStart("biz-1", 100L, 1_900L);

    // then it is a no-op: the ask was pruned, so exactly one sample stands (M16)
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().count()).isEqualTo(1L);
  }

  @Test
  void shouldMeasureReleaseToStartFromMostRecentFree() {
    // given the businessId is freed, re-taken and freed again before the blocked ask starts
    blockAsk("biz-1", 100L);
    freeBusinessId("biz-1", 1_000L);
    freeBusinessId("biz-1", 2_000L);

    // when the blocked cross-partition ask finally starts
    releaseToStart("biz-1", 100L, 2_100L);

    // then the latency is measured from the most recent release, not the first (M16)
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().max(TimeUnit.MILLISECONDS))
        .isEqualTo(100.0);
  }

  @Test
  void shouldNotMeasureSecondBlockedAskAgainstAStaleFreeWhenItsHolderDidNotComplete() {
    // given two asks blocked on the same businessId, freed once, letting the first through
    blockAsk("biz-1", 100L);
    blockAsk("biz-1", 200L);
    freeBusinessId("biz-1", 1_000L);
    releaseToStart("biz-1", 100L, 1_100L);

    // when the second ask starts without a fresh free (its interim holder was banned/migrated and
    // never reported a completion)
    releaseToStart("biz-1", 200L, 5_000L);

    // then only the first ask is measured; the stale free is consumed and does not inflate the
    // second — that holder-not-completed case is the documented blind spot (M16)
    final var timer = registry.get(RELEASE_TO_START_METRIC).timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(100.0);
  }

  @Test
  void shouldMeasureEachConcurrentlyBlockedAskFromItsOwnRelease() {
    // given two asks are blocked on the same businessId
    blockAsk("biz-1", 100L);
    blockAsk("biz-1", 200L);

    // when the first free lets ask 100 through (which re-holds the id), then a second free lets ask
    // 200 through
    freeBusinessId("biz-1", 1_000L);
    releaseToStart("biz-1", 100L, 1_100L);
    freeBusinessId("biz-1", 2_000L);
    releaseToStart("biz-1", 200L, 2_200L);

    // then each ask is measured once from its own release (M16)
    final var timer = registry.get(RELEASE_TO_START_METRIC).timer();
    assertThat(timer.count()).isEqualTo(2L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300.0);
  }

  @Test
  void shouldTrackTheSameBusinessIdInDifferentDomainsIndependently() {
    // given the same businessId is contended in two different process definitions — the same hash
    // {@code P_B} and metrics instance, but distinct uniqueness domains — each with its own ask
    metrics.recordAskBlockedOnBusinessId("biz-1", "process-a", "tenant-1", 100L);
    metrics.recordAskBlockedOnBusinessId("biz-1", "process-b", "tenant-1", 200L);

    // when only the first domain's holder frees the businessId
    metrics.recordBusinessIdFreed("biz-1", "process-a", "tenant-1", 1_000L);

    // and both asks start
    metrics.recordReleaseToStart("biz-1", "process-a", "tenant-1", 100L, 1_400L);
    metrics.recordReleaseToStart("biz-1", "process-b", "tenant-1", 200L, 5_000L);

    // then only the freed domain is measured — the other domain's free never leaked across (M16)
    final var timer = registry.get(RELEASE_TO_START_METRIC).timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(400.0);
  }

  @Test
  void shouldDiscardBlockedAskOnExpirySoALaterReuseIsNotMeasured() {
    // given a blocked ask that is then rejected as expired (it will stop retrying)
    blockAsk("biz-1", 100L);
    discardAsk("biz-1", 100L);

    // when the holder later frees the businessId and a fresh reuse starts
    freeBusinessId("biz-1", 1_000L);
    releaseToStart("biz-1", 200L, 5_000L);

    // then nothing is recorded — the stale blocked ask no longer arms the release (M16)
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().count()).isZero();
  }

  @Test
  void shouldClearBlockedAndFreedBusinessIdsOnRecovery() {
    // given a blocked-and-freed businessId tracked in the previous leadership term
    blockAsk("biz-1", 100L);
    freeBusinessId("biz-1", 1_000L);

    // when recovery drops the in-memory maps
    metrics.onRecovered(null);

    // then a subsequent start is a no-op — the stale tracking is gone (M16)
    releaseToStart("biz-1", 100L, 1_250L);
    assertThat(registry.get(RELEASE_TO_START_METRIC).timer().count()).isZero();
  }

  private double counter(final String name) {
    final var counter = registry.find(name).counter();
    return counter != null ? counter.count() : 0.0;
  }

  private double taggedCount(final String name, final String key, final String value) {
    final var counter = registry.find(name).tag(key, value).counter();
    return counter != null ? counter.count() : 0.0;
  }

  private double requestCount(final String outcome) {
    return taggedCount("zeebe.message.start.cross.partition.requests.total", "outcome", outcome);
  }

  private double replyCount(final String outcome) {
    return taggedCount("zeebe.message.start.cross.partition.replies.total", "outcome", outcome);
  }

  private double lockReleaseSentCount(final String trigger) {
    return taggedCount(
        "zeebe.message.start.cross.partition.lock.releases.sent.total", "trigger", trigger);
  }

  private double lockReleaseCount(final String result) {
    return taggedCount("zeebe.message.start.cross.partition.lock.releases.total", "result", result);
  }

  private double blockedCount(final String reason) {
    return taggedCount("zeebe.message.start.blocked.total", "reason", reason);
  }

  private void blockAsk(final String businessId, final long messageKey) {
    metrics.recordAskBlockedOnBusinessId(businessId, PROCESS_ID, TENANT, messageKey);
  }

  private void freeBusinessId(final String businessId, final long freedAtMillis) {
    metrics.recordBusinessIdFreed(businessId, PROCESS_ID, TENANT, freedAtMillis);
  }

  private void releaseToStart(
      final String businessId, final long messageKey, final long startedAtMillis) {
    metrics.recordReleaseToStart(businessId, PROCESS_ID, TENANT, messageKey, startedAtMillis);
  }

  private void discardAsk(final String businessId, final long messageKey) {
    metrics.discardBlockedAsk(businessId, PROCESS_ID, TENANT, messageKey);
  }
}
