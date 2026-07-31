/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

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
}
