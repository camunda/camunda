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
}
