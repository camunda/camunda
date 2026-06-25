/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.service;

import io.camunda.eventbridge.broker.partitioning.PartitionLeaderReporter;
import io.camunda.eventbridge.broker.request.coordination.BrokerReportPartitionLeaderRequest;
import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reports topic-partition leadership to the metadata-group leader and retries until it is durably
 * acknowledged — the broker side of recording leadership in the replicated registry, the sibling of
 * {@code BrokerRegistrar}. Runs in the same process as the gateway and reuses its {@link
 * BrokerClient}, which routes to the metadata routing group's leader from gossip (with NOT_LEADER
 * retry); the broker's {@code PartitionLifecycle} invokes it via the {@link
 * PartitionLeaderReporter} callback when it becomes a topic partition's Raft leader.
 *
 * <p>A report retries with capped backoff until the leader acks. It stops early when superseded by
 * a higher-term report for the same partition (a re-election), so a deposed leader's report does
 * not retry forever.
 */
@Component
public final class PartitionLeadershipReporter implements PartitionLeaderReporter {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionLeadershipReporter.class);
  private static final Duration INITIAL_RETRY = Duration.ofSeconds(1);
  private static final Duration MAX_RETRY = Duration.ofSeconds(8);

  private final BrokerClient brokerClient;
  private final ScheduledExecutorService scheduler;
  // Highest term reported per "topic#partition", so a superseded (re-elected) report stops
  // retrying.
  private final ConcurrentMap<String, Long> latestTerm = new ConcurrentHashMap<>();

  public PartitionLeadershipReporter(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
    scheduler =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "partition-leader-reporter");
              thread.setDaemon(true);
              return thread;
            });
  }

  @Override
  public void reportLeadership(
      final String topic, final int partitionId, final int leaderNode, final long term) {
    final var key = topic + "#" + partitionId;
    latestTerm.merge(key, term, Math::max);
    send(key, topic, partitionId, leaderNode, term, INITIAL_RETRY);
  }

  private void send(
      final String key,
      final String topic,
      final int partitionId,
      final int leaderNode,
      final long term,
      final Duration delay) {
    final var latest = latestTerm.get(key);
    if (latest == null || term < latest) {
      return; // superseded by a newer leadership term for this partition
    }
    brokerClient
        .sendRequest(
            new BrokerReportPartitionLeaderRequest()
                .wrapRequest(topic, partitionId, leaderNode, term))
        .whenComplete(
            (response, error) -> {
              if (error == null
                  && response.getResponse().getErrorCode() == CoordinationErrorCode.NONE) {
                LOG.debug(
                    "Reported leadership of topic {} partition {} (term {}) to the metadata group",
                    topic,
                    partitionId,
                    term);
                latestTerm.remove(key, term);
                return;
              }
              final var next = delay.compareTo(MAX_RETRY) < 0 ? delay.multipliedBy(2) : MAX_RETRY;
              reschedule(() -> send(key, topic, partitionId, leaderNode, term, next), delay);
            });
  }

  private void reschedule(final Runnable task, final Duration delay) {
    if (!scheduler.isShutdown()) {
      try {
        scheduler.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
      } catch (final java.util.concurrent.RejectedExecutionException ignored) {
        // shutting down
      }
    }
  }
}
