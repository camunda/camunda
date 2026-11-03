/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probe;

import static io.camunda.zeebe.scheduler.clock.ActorClock.currentTimeMillis;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeYieldRequest;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.scheduler.ActorControl;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;

/**
 * Collects probes for a worker's poll, with the long polling of job activation: the poll goes to
 * every partition of the physical tenant, round-robin; when none has a probe, the request is held
 * until a partition announces probes of any of its probe IDs, or until its request timeout.
 *
 * <p>Unlike a job poll, which names one type, a probe poll names every probe ID of its worker, so a
 * held poll waits under each of them and stops waiting under all of them once one wakes it.
 *
 * <p>A dedicated, smaller cousin of {@code LongPollingActivateJobsHandler} and {@code
 * RoundRobinActivateJobsHandler}, which are typed to job requests throughout. It keeps their
 * behavior that matters for probes: round-robin over partitions, a retry when a notification
 * arrived while the partitions were being tried, and handing back what could not be delivered.
 */
public final class LongPollingActivateProbesHandler implements Consumer<ActorControl> {

  private static final Logger LOG = Loggers.LONG_POLLING;
  private static final String PROBES_AVAILABLE_TOPIC = "probesAvailable";

  private final BrokerClient brokerClient;
  private final Duration defaultLongPollingTimeout;
  private final Map<Key, Set<Poll>> waitingPolls = new HashMap<>();
  private final Map<Key, Long> notificationCounts = new HashMap<>();
  private final Map<List<String>, AtomicInteger> nextPartitionByProbeIds = new HashMap<>();
  private ActorControl actor;

  public LongPollingActivateProbesHandler(
      final BrokerClient brokerClient, final Duration defaultLongPollingTimeout) {
    this.brokerClient = brokerClient;
    this.defaultLongPollingTimeout = defaultLongPollingTimeout;
  }

  @Override
  public void accept(final ActorControl actor) {
    this.actor = actor;
  }

  /**
   * Collects probes for the poll and hands them to {@code deliver}, which returns whether the
   * worker got them; probes it did not get are yielded. A {@code requestTimeout} of 0 uses the
   * default long polling timeout; a negative one disables long polling, as for jobs.
   *
   * @param request the poll, with the partition group and the authorization already set
   */
  public void activateProbes(
      final BrokerActivateProbesRequest request,
      final long requestTimeout,
      final Predicate<ProbeBatchRecord> deliver,
      final Consumer<Throwable> onError) {
    final long timeout =
        requestTimeout == 0 ? defaultLongPollingTimeout.toMillis() : Math.max(requestTimeout, 0);
    actor.run(
        () -> {
          final var poll =
              new Poll(
                  request,
                  request.getMaxProbesToActivate(),
                  currentTimeMillis() + timeout,
                  deliver,
                  onError);
          subscribe(request.getPartitionGroup());
          tryPartitions(poll);
        });
  }

  private void subscribe(final String physicalTenantId) {
    // the broker client dedups per (topic, subscriber), so subscribing on every poll is cheap
    brokerClient.subscribeJobAvailableNotification(
        physicalTenantId + "-" + PROBES_AVAILABLE_TOPIC,
        this,
        probeId -> actor.run(() -> onProbesAvailable(physicalTenantId, probeId)));
  }

  private void onProbesAvailable(final String physicalTenantId, final String probeId) {
    final var key = new Key(physicalTenantId, probeId);
    notificationCounts.merge(key, 1L, Long::sum);
    final var polls = waitingPolls.remove(key);
    if (polls != null) {
      for (final Poll poll : polls) {
        if (stopWaiting(poll)) {
          tryPartitions(poll);
        }
      }
    }
  }

  /**
   * Removes the poll from the polls waiting under each of its probe IDs.
   *
   * @return whether it was waiting; a poll another notification or its timeout already took is not
   */
  private boolean stopWaiting(final Poll poll) {
    if (!poll.waiting) {
      return false;
    }
    poll.waiting = false;
    for (final Key key : poll.keys()) {
      final var polls = waitingPolls.get(key);
      if (polls != null && polls.remove(poll) && polls.isEmpty()) {
        waitingPolls.remove(key);
      }
    }
    return true;
  }

  private long notificationCount(final Poll poll) {
    long count = 0;
    for (final Key key : poll.keys()) {
      count += notificationCounts.getOrDefault(key, 0L);
    }
    return count;
  }

  /** Tries every partition once, round-robin, collecting probes into the poll. */
  private void tryPartitions(final Poll poll) {
    final var topology = brokerClient.getTopologyManager().getTopology(poll.physicalTenantId());
    if (topology == null || topology.getPartitions().isEmpty()) {
      complete(poll);
      return;
    }
    final List<Integer> partitions = new ArrayList<>(topology.getPartitions());
    final int start =
        nextPartitionByProbeIds
                .computeIfAbsent(poll.request.getProbeIds(), ids -> new AtomicInteger())
                .getAndIncrement()
            % partitions.size();
    final List<Integer> order = new ArrayList<>();
    for (int i = 0; i < partitions.size(); i++) {
      order.add(partitions.get((start + i) % partitions.size()));
    }
    poll.notificationsAtStart = notificationCount(poll);
    tryPartition(poll, order, 0);
  }

  private void tryPartition(final Poll poll, final List<Integer> partitions, final int index) {
    if (index >= partitions.size() || poll.remaining() <= 0) {
      onPartitionsTried(poll);
      return;
    }
    final var request = poll.request;
    request.setPartitionId(partitions.get(index));
    request.setMaxProbesToActivate(poll.remaining());
    brokerClient
        .sendRequest(request)
        .whenCompleteAsync(
            (response, error) -> {
              if (error == null && response.isRejection()) {
                poll.onError.accept(response.toException());
                return;
              }
              if (error != null || !response.isResponse()) {
                LOG.debug(
                    "Failed to activate probes of {} on partition {}",
                    request.getProbeIds(),
                    partitions.get(index),
                    error);
              } else {
                poll.add(response);
              }
              tryPartition(poll, partitions, index + 1);
            },
            actor);
  }

  private void onPartitionsTried(final Poll poll) {
    if (poll.collected.probes().size() > 0) {
      complete(poll);
      return;
    }
    // a partition announced probes while the others were tried: one may have been missed
    if (notificationCount(poll) != poll.notificationsAtStart) {
      tryPartitions(poll);
      return;
    }
    final long remainingTime = poll.deadline - currentTimeMillis();
    if (remainingTime <= 0) {
      complete(poll);
      return;
    }
    poll.waiting = true;
    for (final Key key : poll.keys()) {
      waitingPolls.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(poll);
    }
    actor.schedule(
        Duration.ofMillis(remainingTime),
        () -> {
          if (stopWaiting(poll)) {
            complete(poll);
          }
        });
  }

  private void complete(final Poll poll) {
    if (poll.deliver.test(poll.collected)) {
      return;
    }
    final List<Long> undelivered = new ArrayList<>();
    for (final LongValue probeKey : poll.collected.probeKeys()) {
      undelivered.add(probeKey.getValue());
    }
    if (!undelivered.isEmpty()) {
      LOG.info(
          "Failed to deliver {} probes of {} to the worker; yielding them",
          undelivered.size(),
          poll.request.getProbeIds());
      undelivered.forEach(probeKey -> yieldProbe(probeKey, poll.physicalTenantId()));
    }
  }

  private void yieldProbe(final long probeKey, final String physicalTenantId) {
    final var request = new BrokerProbeYieldRequest(probeKey);
    request.setAuthorization(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    request.setPartitionGroup(physicalTenantId);
    brokerClient
        .sendRequestWithRetry(request)
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                LOG.info("Failed to yield probe {}: {}", probeKey, error.getMessage());
              }
            });
  }

  private record Key(String physicalTenantId, String probeId) {}

  private static final class Poll {

    private final BrokerActivateProbesRequest request;
    private final int maxProbes;
    private final long deadline;
    private final Predicate<ProbeBatchRecord> deliver;
    private final Consumer<Throwable> onError;
    private final ProbeBatchRecord collected;
    private long notificationsAtStart;
    private boolean waiting;

    private Poll(
        final BrokerActivateProbesRequest request,
        final int maxProbes,
        final long deadline,
        final Predicate<ProbeBatchRecord> deliver,
        final Consumer<Throwable> onError) {
      this.request = request;
      this.maxProbes = maxProbes;
      this.deadline = deadline;
      this.deliver = deliver;
      this.onError = onError;
      collected =
          new ProbeBatchRecord()
              .setProbeIds(request.getProbeIds())
              .setWorker(request.getWorker())
              .setMaxProbesToActivate(maxProbes)
              .setTenantIds(request.getTenantIds())
              .setTenantFilter(request.getTenantFilter());
    }

    private String physicalTenantId() {
      return request.getPartitionGroup();
    }

    private List<Key> keys() {
      return request.getProbeIds().stream()
          .distinct()
          .map(probeId -> new Key(physicalTenantId(), probeId))
          .toList();
    }

    private int remaining() {
      return maxProbes - collected.probeKeys().size();
    }

    private void add(final BrokerResponse<ProbeBatchRecord> response) {
      final var batch = response.getResponse();
      int index = 0;
      for (final ProbeRecord probe : batch.probes()) {
        collected.addProbe(batch.probeKeys().get(index++).getValue(), probe);
      }
    }
  }
}
