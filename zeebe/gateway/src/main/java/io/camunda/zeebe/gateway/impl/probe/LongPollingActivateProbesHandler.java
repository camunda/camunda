/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probe;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.broker.client.impl.PartitionIdIterator;
import io.camunda.zeebe.broker.client.impl.RoundRobinDispatchStrategy;
import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateProbesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerProbeYieldRequest;
import io.camunda.zeebe.gateway.impl.configuration.LongPollingCfg;
import io.camunda.zeebe.gateway.impl.job.ResponseObserver;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Collects probes for a worker's poll with the long polling of job activation (spec section 4.2):
 * the poll goes to every partition of its physical tenant, round-robin; when no partition has a
 * probe, the poll is held until a partition announces a probe of one of its probe IDs on {@code
 * <physicalTenant>-probesAvailable}, or until its request timeout.
 *
 * <p>A job poll names one type, a probe poll names a list of probe IDs (D50). So a poll is
 * registered under {@code (physical tenant, probe ID)} for each of its probe IDs, wakes on a
 * notification for any of them, and is removed from every key when it ends.
 *
 * <p>Probes that cannot be delivered, because the worker's request was cancelled or the response
 * would exceed the maximum message size, are yielded back to their partition (D41), as {@link
 * io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler} fails jobs it could not deliver.
 *
 * <p>All state is confined to the actor.
 */
@NullMarked
public final class LongPollingActivateProbesHandler implements Consumer<ActorControl> {

  private static final Logger LOG = Loggers.LONG_POLLING;
  private static final String PROBES_AVAILABLE_TOPIC = "probesAvailable";
  private static final String ERROR_MSG_RESOURCE_EXHAUSTED =
      "Expected to activate probes of %s, but no probes available and at least one broker returned 'RESOURCE_EXHAUSTED'. Please try again later.";
  private static final String PURGE_ERROR_MSG =
      "Cluster was purged; pending probe activation requests have been cancelled. Please retry.";
  private static final String NOT_SENT_MSG = "Failed to send activated probes to client";

  private final BrokerClient brokerClient;
  private final BrokerTopologyManager topologyManager;
  private final long maxMessageSize;
  private final boolean longPollingEnabled;
  private final Duration defaultLongPollingTimeout;
  private final Function<String, Exception> resourceExhaustedExceptionProvider;
  private final Function<String, Throwable> requestCanceledExceptionProvider;

  // keeps its round-robin state per partition group, so it is bounded by the physical tenants
  private final RoundRobinDispatchStrategy roundRobin = new RoundRobinDispatchStrategy();
  private final Set<Poll> openPolls = new HashSet<>();
  private final Map<Key, Set<Poll>> pollsByKey = new HashMap<>();

  @SuppressWarnings("NullAway.Init") // set when the actor starts, before any poll
  private ActorControl actor;

  /**
   * @param longPolling the long polling configuration of job activation: probes share its switch
   *     and its default timeout
   */
  public LongPollingActivateProbesHandler(
      final BrokerClient brokerClient,
      final long maxMessageSize,
      final LongPollingCfg longPolling,
      final Function<String, Exception> resourceExhaustedExceptionProvider,
      final Function<String, Throwable> requestCanceledExceptionProvider) {
    this.brokerClient = brokerClient;
    topologyManager = brokerClient.getTopologyManager();
    this.maxMessageSize = maxMessageSize;
    longPollingEnabled = longPolling.isEnabled();
    defaultLongPollingTimeout = Duration.ofMillis(longPolling.getTimeout());
    this.resourceExhaustedExceptionProvider = resourceExhaustedExceptionProvider;
    this.requestCanceledExceptionProvider = requestCanceledExceptionProvider;
  }

  @Override
  public void accept(final ActorControl actor) {
    this.actor = actor;
  }

  /**
   * Collects probes for the poll and hands them to the observer.
   *
   * @param request the poll, with its authorization and partition group already set
   * @param setCancelHandler receives the action to run when the worker's request is cancelled
   * @param requestTimeout how long to hold the poll when no probe is waiting: 0 uses the configured
   *     long polling timeout, a negative value does not hold the poll, as for jobs
   */
  public void activateProbes(
      final BrokerActivateProbesRequest request,
      final ResponseObserver<ProbeBatchRecord> responseObserver,
      final Consumer<Runnable> setCancelHandler,
      final long requestTimeout) {
    final var poll = new Poll(request, responseObserver, requestTimeout);
    // set before the actor call: the observer may not be changed once this method returns
    setCancelHandler.accept(() -> actor.run(() -> onCancel(poll)));
    actor.run(
        () -> {
          if (!responseObserver.isCancelled()) {
            open(poll);
          }
        });
  }

  /**
   * Fails every open poll so workers poll again. Called when the cluster incarnation changes, for
   * example after a purge, as for job polls.
   */
  public void onClusterIncarnationChanged() {
    if (actor == null) {
      return;
    }
    actor.run(
        () -> {
          LOG.info("Cluster purge detected, cancelling {} open probe polls", openPolls.size());
          final var error = new RuntimeException(PURGE_ERROR_MSG);
          List.copyOf(openPolls).forEach(poll -> fail(poll, error));
        });
  }

  /** The polls registered under the probe ID, for tests. Call it on the actor's thread. */
  int registeredPolls(final String physicalTenantId, final String probeId) {
    final var polls = pollsByKey.get(new Key(physicalTenantId, probeId));
    return polls == null ? 0 : polls.size();
  }

  /** The open polls, for tests. Call it on the actor's thread. */
  int openPolls() {
    return openPolls.size();
  }

  private void open(final Poll poll) {
    subscribe(poll.physicalTenantId);
    openPolls.add(poll);
    for (final Key key : poll.keys) {
      pollsByKey.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(poll);
    }
    tryPartitions(poll);
  }

  /**
   * Safe to call for every poll: the broker client dedups subscriptions by (topic, subscriber). The
   * topic is not the jobs' one, so a probe never wakes a job poll and a job never wakes a probe
   * poll.
   */
  private void subscribe(final String physicalTenantId) {
    brokerClient.subscribeJobAvailableNotification(
        physicalTenantId + "-" + PROBES_AVAILABLE_TOPIC,
        this,
        probeId -> actor.run(() -> onProbesAvailable(new Key(physicalTenantId, probeId))));
  }

  private void onProbesAvailable(final Key key) {
    final var polls = pollsByKey.get(key);
    if (polls == null) {
      return;
    }
    for (final Poll poll : List.copyOf(polls)) {
      if (poll.trying) {
        // a partition already tried may have the probe: try every partition again afterwards
        poll.notifiedWhileTrying = true;
      } else if (!poll.done) {
        tryPartitions(poll);
      }
    }
  }

  private void onCancel(final Poll poll) {
    // a poll that is trying partitions ends when the current response arrives, so the probes it
    // collected are yielded
    if (!poll.done && !poll.trying) {
      close(poll);
    }
  }

  private void tryPartitions(final Poll poll) {
    poll.trying = true;
    poll.notifiedWhileTrying = false;
    poll.resourceExhausted = false;
    poll.pollSamePartition = false;
    final BrokerClusterState topology = topologyManager.getTopology(poll.physicalTenantId);
    poll.partitions =
        topology == null
            ? null
            : new PartitionIdIterator(
                firstPartition(poll.physicalTenantId),
                topology.getPartitionsCount(),
                topologyManager,
                poll.physicalTenantId);
    tryNextPartition(poll);
  }

  private int firstPartition(final String physicalTenantId) {
    final int partitionId = roundRobin.determinePartition(topologyManager, physicalTenantId);
    return partitionId == BrokerClusterState.PARTITION_ID_NULL
        ? Protocol.START_PARTITION_ID
        : partitionId;
  }

  private void tryNextPartition(final Poll poll) {
    if (poll.responseObserver.isCancelled()) {
      deliver(poll);
      return;
    }
    final var partitions = poll.partitions;
    if (poll.remaining() <= 0 || poll.full || partitions == null) {
      onPartitionsTried(poll);
      return;
    }
    final int partitionId;
    if (poll.pollSamePartition) {
      partitionId = partitions.getCurrentPartitionId();
    } else if (partitions.hasNext()) {
      partitionId = partitions.next();
    } else {
      onPartitionsTried(poll);
      return;
    }
    final var request = poll.request;
    request.setPartitionId(partitionId);
    request.setMaxProbesToActivate(poll.remaining());
    brokerClient
        .sendRequest(request)
        .whenCompleteAsync((response, error) -> onResponse(poll, response, error), actor);
  }

  private void onResponse(
      final Poll poll,
      final @Nullable BrokerResponse<ProbeBatchRecord> response,
      final @Nullable Throwable error) {
    if (error == null && response != null && response.isResponse()) {
      // a response that is not an error or a rejection always has a body
      onProbesCollected(poll, Objects.requireNonNull(response.getResponse()));
    } else {
      onFailure(poll, error != null ? error : Objects.requireNonNull(response).toException());
    }
  }

  private void onProbesCollected(final Poll poll, final ProbeBatchRecord batch) {
    if (poll.done) {
      // the poll ended meanwhile, at its timeout or by a purge: nobody gets these probes
      yieldProbes(probeKeys(batch), poll.physicalTenantId);
      return;
    }
    collect(poll, batch);
    tryNextPartition(poll);
  }

  private void onFailure(final Poll poll, final Throwable failure) {
    if (poll.done) {
      return;
    }
    if (failure instanceof BrokerRejectionException) {
      fail(poll, failure);
      return;
    }
    if (isResourceExhausted(failure)) {
      poll.resourceExhausted = true;
    } else {
      LOG.warn(
          "Failed to activate probes of {} on partition {}",
          poll.probeIds(),
          poll.request.getPartitionId(),
          failure);
    }
    poll.pollSamePartition = false;
    tryNextPartition(poll);
  }

  /**
   * Adds the probes of a partition's response to the poll, up to the maximum message size, as the
   * job response mappers do. Probes beyond it are yielded, and the poll stops collecting.
   */
  private void collect(final Poll poll, final ProbeBatchRecord batch) {
    final List<Long> tooLarge = new ArrayList<>();
    final Iterator<LongValue> keys = batch.probeKeys().iterator();
    final Iterator<ProbeRecord> probes = batch.probes().iterator();
    while (keys.hasNext() && probes.hasNext()) {
      final long probeKey = keys.next().getValue();
      final ProbeRecord probe = probes.next();
      // the size of the broker's message, not of the REST response, as for jobs
      final int size = probe.getLength();
      if (!poll.full && poll.collectedSize + size <= maxMessageSize) {
        poll.collected.addProbe(probeKey, probe);
        poll.collectedSize += size;
      } else {
        poll.full = true;
        tooLarge.add(probeKey);
      }
    }
    if (!tooLarge.isEmpty()) {
      LOG.info(
          "Failed to send {} activated probes of {} (with probe keys: {}) to client, because the response is bigger than the maximum allowed message size {}",
          tooLarge.size(),
          poll.probeIds(),
          tooLarge,
          maxMessageSize);
      yieldProbes(tooLarge, poll.physicalTenantId);
    }
    // the engine truncates an empty batch when its first probe cannot fit beside the poll: asking
    // again would get the same answer, so only a batch with probes is asked for more
    poll.pollSamePartition = batch.isTruncated() && batch.probeKeys().size() > 0;
  }

  private void onPartitionsTried(final Poll poll) {
    poll.trying = false;
    if (poll.responseObserver.isCancelled()
        || poll.collected.probeKeys().size() > 0
        // a full poll collects nothing more, even if it holds no probe
        || poll.full) {
      deliver(poll);
    } else if (poll.resourceExhausted) {
      fail(
          poll,
          resourceExhaustedExceptionProvider.apply(
              ERROR_MSG_RESOURCE_EXHAUSTED.formatted(poll.probeIds())));
    } else if (!longPollingEnabled || poll.requestTimeout < 0) {
      deliver(poll);
    } else {
      // started at the first round without probes, as for jobs, so a poll notified during every
      // round still ends at its timeout
      startTimeout(poll);
      if (poll.notifiedWhileTrying) {
        tryPartitions(poll);
      }
    }
  }

  private void startTimeout(final Poll poll) {
    if (poll.timer != null) {
      return;
    }
    final Duration timeout =
        poll.requestTimeout == 0
            ? defaultLongPollingTimeout
            : Duration.ofMillis(poll.requestTimeout);
    LOG.trace(
        "Worker '{}' polled probes of {}, but none are available. The poll is held until a probe of these IDs is available or for {}.",
        poll.request.getRequestWriter().getWorker(),
        poll.probeIds(),
        timeout);
    poll.timer = actor.schedule(timeout, () -> onTimeout(poll));
  }

  /**
   * Answers the poll at once, as for jobs, even while a partition's response is on its way: the
   * probes of that response are yielded when it arrives.
   */
  private void onTimeout(final Poll poll) {
    poll.timer = null;
    if (!poll.done) {
      deliver(poll);
    }
  }

  /** Answers the worker with the collected probes, or yields them if the worker is gone. */
  private void deliver(final Poll poll) {
    close(poll);
    final var probeKeys = probeKeys(poll.collected);
    if (poll.responseObserver.isCancelled()) {
      if (!probeKeys.isEmpty()) {
        logNotSent(poll, probeKeys, "the request was cancelled");
        yieldProbes(probeKeys, poll.physicalTenantId);
      }
      return;
    }
    try {
      if (!probeKeys.isEmpty()) {
        poll.responseObserver.onNext(poll.collected);
      }
      poll.responseObserver.onCompleted();
    } catch (final Exception e) {
      logNotSent(poll, probeKeys, e.getMessage());
      yieldProbes(probeKeys, poll.physicalTenantId);
      notifyError(
          poll,
          requestCanceledExceptionProvider.apply(
              NOT_SENT_MSG + ", failed with: " + e.getMessage()));
    }
  }

  private void fail(final Poll poll, final Throwable error) {
    close(poll);
    yieldProbes(probeKeys(poll.collected), poll.physicalTenantId);
    notifyError(poll, error);
  }

  private void notifyError(final Poll poll, final Throwable error) {
    try {
      poll.responseObserver.onError(error);
    } catch (final Exception e) {
      LOG.warn("Failed to send terminating error to client.", e);
    }
  }

  /** Ends the poll: it is no longer registered under any probe ID and its timer is cancelled. */
  private void close(final Poll poll) {
    poll.done = true;
    final var timer = poll.timer;
    if (timer != null) {
      timer.cancel();
      poll.timer = null;
    }
    openPolls.remove(poll);
    for (final Key key : poll.keys) {
      final var polls = pollsByKey.get(key);
      if (polls != null && polls.remove(poll) && polls.isEmpty()) {
        pollsByKey.remove(key);
      }
    }
  }

  /**
   * Hands the probes back to their partitions, so the next poll collects them (D41). Sent as the
   * anonymous user, as the job handler fails jobs it could not deliver: the worker's poll was
   * authorized when it collected them.
   */
  private void yieldProbes(final List<Long> probeKeys, final String physicalTenantId) {
    for (final long probeKey : probeKeys) {
      final var request = new BrokerProbeYieldRequest(probeKey);
      request.setAuthorization(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
      request.setPartitionGroup(physicalTenantId);
      brokerClient
          .sendRequestWithRetry(request)
          .whenCompleteAsync(
              (response, error) -> {
                if (error != null) {
                  Loggers.GATEWAY_LOGGER.info(
                      "Failed to yield probe {} due to {}", probeKey, error.getMessage());
                } else if (!response.isResponse()) {
                  Loggers.GATEWAY_LOGGER.info(
                      "Failed to yield probe {} due to broker response {}", probeKey, response);
                }
              },
              actor);
    }
  }

  private void logNotSent(
      final Poll poll, final List<Long> probeKeys, final @Nullable String reason) {
    Loggers.GATEWAY_LOGGER.info(
        "Failed to send {} activated probes of {} (with probe keys: {}) to client, because: {}",
        probeKeys.size(),
        poll.probeIds(),
        probeKeys,
        reason);
  }

  private static List<Long> probeKeys(final ProbeBatchRecord batch) {
    final List<Long> keys = new ArrayList<>();
    for (final LongValue key : batch.probeKeys()) {
      keys.add(key.getValue());
    }
    return keys;
  }

  private static boolean isResourceExhausted(final Throwable error) {
    return error instanceof final BrokerErrorException brokerError
        && brokerError.getError().getCode() == ErrorCode.RESOURCE_EXHAUSTED;
  }

  private record Key(String physicalTenantId, String probeId) {}

  /** One worker's poll, from its arrival until it is answered, failed or cancelled. */
  private static final class Poll {

    private final BrokerActivateProbesRequest request;
    private final ResponseObserver<ProbeBatchRecord> responseObserver;
    private final long requestTimeout;
    private final String physicalTenantId;
    private final List<Key> keys;
    private final int maxProbes;
    private final ProbeBatchRecord collected = new ProbeBatchRecord();
    private long collectedSize;
    private boolean full;

    private @Nullable PartitionIdIterator partitions;
    private boolean pollSamePartition;
    private boolean resourceExhausted;
    private boolean trying;
    private boolean notifiedWhileTrying;
    private boolean done;
    private @Nullable ScheduledTimer timer;

    private Poll(
        final BrokerActivateProbesRequest request,
        final ResponseObserver<ProbeBatchRecord> responseObserver,
        final long requestTimeout) {
      this.request = request;
      this.responseObserver = responseObserver;
      this.requestTimeout = requestTimeout;
      physicalTenantId = Objects.requireNonNull(request.getPartitionGroup(), "partition group");
      final var value = request.getRequestWriter();
      keys =
          value.getProbeIds().stream()
              .distinct()
              .map(probeId -> new Key(physicalTenantId, probeId))
              .toList();
      maxProbes = value.getMaxProbesToActivate();
      collected
          .setProbeIds(value.getProbeIds())
          .setWorker(value.getWorker())
          .setMaxProbesToActivate(maxProbes)
          .setTenantIds(value.getTenantIds())
          .setTenantFilter(value.getTenantFilter());
    }

    private List<String> probeIds() {
      return keys.stream().map(Key::probeId).toList();
    }

    private int remaining() {
      return maxProbes - collected.probeKeys().size();
    }
  }
}
