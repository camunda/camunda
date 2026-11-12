/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.metrics;

import static io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.REQUESTS_QUEUED_CURRENT;
import static io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.RequestsQueuedKeyNames.TYPE;

import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.ActivationKeyNames;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayKeyNames;
import io.camunda.zeebe.gateway.metrics.LongPollingMetricsDoc.GatewayProtocol;
import io.camunda.zeebe.util.micrometer.BoundedMeterCache;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.camunda.zeebe.util.micrometer.StatefulGauge;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the number of long polling requests blocked per job type, for one physical tenant. Every
 * instance is tagged with its physical tenant; use {@link LongPollingMetricsFactory} to obtain one
 * rather than constructing it directly.
 */
public sealed class LongPollingMetrics {

  private final BoundedMeterCache<StatefulGauge> requestsQueued;
  private final MeterRegistry registry;
  private final Tags tags;
  private final Map<String, DistributionSummary> summaries = new ConcurrentHashMap<>();

  LongPollingMetrics(
      final MeterRegistry registry,
      final GatewayProtocol gatewayProtocol,
      final String physicalTenantId) {
    final var provider =
        StatefulGauge.builder(REQUESTS_QUEUED_CURRENT.getName())
            .description(REQUESTS_QUEUED_CURRENT.getDescription())
            .tag(GatewayKeyNames.GATEWAY_PROTOCOL.asString(), gatewayProtocol.value())
            .tag(
                PartitionKeyNames.PHYSICAL_TENANT.asString(),
                Objects.requireNonNull(
                    physicalTenantId, PartitionKeyNames.PHYSICAL_TENANT.asString()))
            .withRegistry(registry);

    requestsQueued = BoundedMeterCache.of(registry, provider, TYPE);
    this.registry = registry;
    tags =
        Tags.of(
            GatewayKeyNames.GATEWAY_PROTOCOL.asString(),
            gatewayProtocol.value(),
            PartitionKeyNames.PHYSICAL_TENANT.asString(),
            physicalTenantId);
  }

  protected LongPollingMetrics(final BoundedMeterCache<StatefulGauge> requestsQueued) {
    this.requestsQueued = requestsQueued;
    registry = null;
    tags = Tags.empty();
  }

  /**
   * Returns an instance of {@link LongPollingMetrics} which does nothing. Mostly useful for
   * testing.
   */
  public static LongPollingMetrics noop() {
    return new Noop();
  }

  /** Sets the number of long polling requests which are idle for a given job type */
  public void setBlockedRequestsCount(final String type, final int count) {
    requestsQueued.get(type).set(count);
  }

  /** Records what it took to activate jobs for one poll */
  public void recordPoll(final JobActivationPollStats stats) {
    final var modeTags =
        tags.and(ActivationKeyNames.MODE.asString(), stats.parallel() ? "parallel" : "sequential");
    summary(LongPollingMetricsDoc.ACTIVATION_REQUESTS, modeTags).record(stats.requests());
    summary(LongPollingMetricsDoc.ACTIVATION_ROUNDS, modeTags).record(stats.rounds());
    summary(LongPollingMetricsDoc.ACTIVATION_JOBS, modeTags).record(stats.jobs());
    summary(LongPollingMetricsDoc.ACTIVATION_SURPLUS_JOBS, modeTags).record(stats.surplusJobs());
    stats
        .jobsPerPartition()
        .forEach(
            (partitionId, jobs) ->
                summary(
                        LongPollingMetricsDoc.ACTIVATION_PARTITION_JOBS,
                        modeTags.and(
                            PartitionKeyNames.PARTITION.asString(), partitionId.toString()))
                    .record(jobs));
  }

  private DistributionSummary summary(final LongPollingMetricsDoc doc, final Tags summaryTags) {
    return summaries.computeIfAbsent(
        doc.getName() + summaryTags,
        key ->
            DistributionSummary.builder(doc.getName())
                .description(doc.getDescription())
                .tags(summaryTags)
                .register(registry));
  }

  private static final class Noop extends LongPollingMetrics {

    private Noop() {
      super(null);
    }

    @Override
    public void setBlockedRequestsCount(final String type, final int count) {}

    @Override
    public void recordPoll(final JobActivationPollStats stats) {}
  }
}
