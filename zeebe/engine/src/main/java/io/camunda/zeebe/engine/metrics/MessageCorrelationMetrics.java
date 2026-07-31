/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.AskOutcome;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.BlockReason;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.MessageCorrelationKeyNames;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReleaseResult;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReleaseTrigger;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.ReplyOutcome;
import io.camunda.zeebe.engine.metrics.MessageCorrelationMetricsDoc.RequestOutcome;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Instruments the Business-ID message-start correlation feature: the cross-partition uniqueness
 * handshake, the pending-ask retry/back-off, and the correlation-key lock-release path. Meters are
 * documented in {@link MessageCorrelationMetricsDoc}.
 *
 * <p>All recording happens on live leader-only paths (command processors, behaviors and
 * schedulers), never in event appliers, so replay does not double-count. Counters tagged by a
 * closed enum are registered lazily per tag value using the {@link EnumMap} idiom.
 *
 * <p>The cross-partition ask-duration timer (M7) keeps an in-memory {@link Timer.Sample} per
 * outstanding ask. Those samples are mutated only from the processing actor (the ask is started by
 * a behavior, stopped by the STARTED reply processor or the message-expiry processor, all on {@code
 * P_K}), never from the scheduler actors that touch the counters, so a plain {@link HashMap} is
 * safe. They are dropped on recovery ({@link #onRecovered}): samples from a previous leadership
 * term would otherwise record meaningless durations.
 */
public final class MessageCorrelationMetrics implements StreamProcessorLifecycleAware {

  private final MeterRegistry registry;

  private final Map<RequestOutcome, Counter> requestCounters = new EnumMap<>(RequestOutcome.class);
  private final Map<ReplyOutcome, Counter> replyCounters = new EnumMap<>(ReplyOutcome.class);
  private final Map<ReleaseTrigger, Counter> lockReleaseSentCounters =
      new EnumMap<>(ReleaseTrigger.class);
  private final Map<ReleaseResult, Counter> lockReleaseCounters =
      new EnumMap<>(ReleaseResult.class);
  private final Map<BlockReason, Counter> blockedCounters = new EnumMap<>(BlockReason.class);
  private final Map<AskOutcome, Timer> askDurationTimers = new EnumMap<>(AskOutcome.class);

  /**
   * Outstanding ask-duration samples keyed by {@code messageKey → processDefinitionKey}. A single
   * message can fan out to several process definitions, and expiry must close all of them by
   * messageKey alone, hence the nested map.
   */
  private final Map<Long, Map<Long, Timer.Sample>> pendingAskSamples = new HashMap<>();

  private final Counter askCounter;
  private final Counter askRetryCounter;
  private final Counter lockReleaseQueryCounter;
  private final Counter dedupSweptCounter;
  private final DistributionSummary lockReleaseQueryBatchSize;

  public MessageCorrelationMetrics(final MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "must specify a registry");

    askCounter = registerCounter(MessageCorrelationMetricsDoc.CROSS_PARTITION_ASKS);
    askRetryCounter = registerCounter(MessageCorrelationMetricsDoc.CROSS_PARTITION_ASK_RETRIES);
    lockReleaseQueryCounter = registerCounter(MessageCorrelationMetricsDoc.LOCK_RELEASE_QUERIES);
    dedupSweptCounter = registerCounter(MessageCorrelationMetricsDoc.DEDUP_SWEPT);

    final var batchSizeDoc = MessageCorrelationMetricsDoc.LOCK_RELEASE_QUERY_BATCH_SIZE;
    lockReleaseQueryBatchSize =
        DistributionSummary.builder(batchSizeDoc.getName())
            .description(batchSizeDoc.getDescription())
            .serviceLevelObjectives(batchSizeDoc.getDistributionSLOs())
            .register(registry);
  }

  /** M1: records the outcome of the cross-partition REQUEST decision ladder on {@code P_B}. */
  public void crossPartitionRequest(final RequestOutcome outcome) {
    requestCounters
        .computeIfAbsent(
            outcome,
            o ->
                registerOutcomeCounter(
                    MessageCorrelationMetricsDoc.CROSS_PARTITION_REQUESTS, o.getLabel()))
        .increment();
  }

  /** M2: records a cross-partition reply outcome processed on {@code P_K}. */
  public void crossPartitionReply(final ReplyOutcome outcome) {
    replyCounters
        .computeIfAbsent(
            outcome,
            o ->
                registerOutcomeCounter(
                    MessageCorrelationMetricsDoc.CROSS_PARTITION_REPLIES, o.getLabel()))
        .increment();
  }

  /** M3: records a newly-registered cross-partition ask dispatched from {@code P_K}. */
  public void crossPartitionAskSent() {
    askCounter.increment();
  }

  /** M8: records a cross-partition ask retry sent by the pending-ask scheduler on {@code P_K}. */
  public void crossPartitionAskRetried() {
    askRetryCounter.increment();
  }

  /**
   * M9: records a correlation-key lock-release reconciliation query dispatched from {@code P_K}.
   */
  public void lockReleaseQuerySent() {
    lockReleaseQueryCounter.increment();
  }

  /** M10: records the holder count of a correlation-key lock-release reconciliation query. */
  public void lockReleaseQueryBatchSize(final int holders) {
    lockReleaseQueryBatchSize.record(holders);
  }

  /** M11: records swept expired cross-partition dedup entries on {@code P_B}. */
  public void dedupSwept(final int count) {
    dedupSweptCounter.increment(count);
  }

  /** M12: records a correlation-key lock-release command sent to {@code P_K}. */
  public void lockReleaseSent(final ReleaseTrigger trigger) {
    lockReleaseSentCounters
        .computeIfAbsent(
            trigger,
            t ->
                registerTaggedCounter(
                    MessageCorrelationMetricsDoc.LOCK_RELEASES_SENT,
                    MessageCorrelationKeyNames.TRIGGER.asString(),
                    t.getLabel()))
        .increment();
  }

  /** M13: records a correlation-key lock-release outcome processed on {@code P_K}. */
  public void lockReleased(final ReleaseResult result) {
    lockReleaseCounters
        .computeIfAbsent(
            result,
            r ->
                registerTaggedCounter(
                    MessageCorrelationMetricsDoc.LOCK_RELEASES,
                    MessageCorrelationKeyNames.RESULT.asString(),
                    r.getLabel()))
        .increment();
  }

  /**
   * M14: records a message-start correlation left buffered by an active holder on the message
   * partition, attributed to the uniqueness gate(s) that blocked it.
   */
  public void messageStartBlocked(final BlockReason reason) {
    blockedCounters
        .computeIfAbsent(
            reason,
            r ->
                registerTaggedCounter(
                    MessageCorrelationMetricsDoc.MESSAGE_START_BLOCKED,
                    MessageCorrelationKeyNames.REASON.asString(),
                    r.getLabel()))
        .increment();
  }

  /**
   * M7: starts the ask-duration timer for a cross-partition ask newly dispatched from {@code P_K}.
   * No-op if a sample for this {@code (messageKey, processDefinitionKey)} already exists, so
   * retries keep accruing against the original dispatch rather than restarting the clock.
   */
  public void startCrossPartitionAsk(final long messageKey, final long processDefinitionKey) {
    pendingAskSamples
        .computeIfAbsent(messageKey, k -> new HashMap<>())
        .computeIfAbsent(processDefinitionKey, k -> Timer.start(registry));
  }

  /**
   * M7: stops the ask-duration timer with {@code outcome=started} when {@code P_K} processes the
   * STARTED reply. No-op if no sample is tracked (e.g. after a leader change cleared it).
   */
  public void completeCrossPartitionAskStarted(
      final long messageKey, final long processDefinitionKey) {
    final var byProcessDefinition = pendingAskSamples.get(messageKey);
    if (byProcessDefinition == null) {
      return;
    }
    final var sample = byProcessDefinition.remove(processDefinitionKey);
    if (byProcessDefinition.isEmpty()) {
      pendingAskSamples.remove(messageKey);
    }
    if (sample != null) {
      sample.stop(askDurationTimer(AskOutcome.STARTED));
    }
  }

  /**
   * M7: stops every outstanding ask-duration timer for an expiring message with {@code
   * outcome=expired} on {@code P_K}. Called for every expiring buffered message; a no-op for the
   * common case with no pending cross-partition ask.
   */
  public void expireCrossPartitionAsks(final long messageKey) {
    final var byProcessDefinition = pendingAskSamples.remove(messageKey);
    if (byProcessDefinition == null) {
      return;
    }
    final var timer = askDurationTimer(AskOutcome.EXPIRED);
    byProcessDefinition.values().forEach(sample -> sample.stop(timer));
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    // Samples belong to the previous leadership term; recording them would produce bogus durations.
    pendingAskSamples.clear();
  }

  private Timer askDurationTimer(final AskOutcome outcome) {
    return askDurationTimers.computeIfAbsent(
        outcome,
        o ->
            MicrometerUtil.buildTimer(MessageCorrelationMetricsDoc.CROSS_PARTITION_ASK_DURATION)
                .tag(MessageCorrelationKeyNames.OUTCOME.asString(), o.getLabel())
                .minimumExpectedValue(Duration.ofMillis(10))
                .register(registry));
  }

  private Counter registerCounter(final MessageCorrelationMetricsDoc doc) {
    return Counter.builder(doc.getName()).description(doc.getDescription()).register(registry);
  }

  private Counter registerOutcomeCounter(
      final MessageCorrelationMetricsDoc doc, final String outcome) {
    return registerTaggedCounter(doc, MessageCorrelationKeyNames.OUTCOME.asString(), outcome);
  }

  private Counter registerTaggedCounter(
      final MessageCorrelationMetricsDoc doc, final String tagKey, final String tagValue) {
    return Counter.builder(doc.getName())
        .description(doc.getDescription())
        .tag(tagKey, tagValue)
        .register(registry);
  }
}
