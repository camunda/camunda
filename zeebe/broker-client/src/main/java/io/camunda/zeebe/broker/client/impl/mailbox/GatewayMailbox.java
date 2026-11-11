/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl.mailbox;

import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.mailbox.AdaptiveBackoff;
import io.camunda.zeebe.mailbox.InboxSender;
import io.camunda.zeebe.mailbox.MeteredMailboxStore;
import io.camunda.zeebe.mailbox.PollLoop;
import io.camunda.zeebe.mailbox.ResponseAwaiter;
import io.camunda.zeebe.mailbox.Scheduler;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import io.camunda.zeebe.mailbox.leadership.LiveLeaders;
import io.camunda.zeebe.mailbox.leadership.MeteredLeaseStore;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The way for a gateway to reach a partition leader that it cannot see. It keeps a copy of the
 * leaders that the store records. For a partition with a recorded leader it puts the request into
 * the inbox of the partition, and collects the response from its outbox.
 *
 * <p>A request is never sent again after a timeout, because the leader may have processed it.
 *
 * <p>Thread-safe. Call {@link #start()} before sending and {@link #close()} at the end.
 */
public final class GatewayMailbox implements AutoCloseable {

  /**
   * The time from sending a request through the mailbox to its response. Tags: {@code partition},
   * and {@code outcome}: {@code success}, {@code timeout}, {@code not_sent} if the insert into the
   * inbox failed, or {@code failure}.
   */
  public static final String REQUEST_LATENCY = "zeebe.mailbox.gateway.request.latency";

  public static final String PENDING_REQUESTS = "zeebe.mailbox.gateway.pending.requests";
  public static final String KNOWN_LEADERS = "zeebe.mailbox.gateway.known.leaders";

  private static final Logger LOG = LoggerFactory.getLogger(GatewayMailbox.class);

  /** How old the copy of the recorded leaders may get. */
  private static final Duration LEADER_REFRESH_INTERVAL = Duration.ofSeconds(1);

  private final GatewayMailboxSetup setup;
  private final InboxSender sender;
  private final ResponseAwaiter awaiter;
  private final ScheduledExecutorService pollExecutor;
  private final PollLoop pollLoop;
  private final ScheduledExecutorService leaderExecutor;
  private final PollLoop leaderLoop;
  private final Supplier<List<PartitionLeader>> leaderSource;
  private final MeterRegistry registry;
  private final List<Meter> gauges;
  private volatile Map<Integer, PartitionLeader> leaders = Map.of();

  public GatewayMailbox(final GatewayMailboxSetup setup) {
    this(setup, new SimpleMeterRegistry());
  }

  /**
   * @param registry receives the metrics of the mailbox and of the calls to its stores
   */
  public GatewayMailbox(final GatewayMailboxSetup setup, final MeterRegistry registry) {
    this(setup, InstantSource.system(), System::nanoTime, registry);
  }

  GatewayMailbox(final GatewayMailboxSetup setup, final InstantSource clock) {
    this(setup, clock, System::nanoTime);
  }

  GatewayMailbox(
      final GatewayMailboxSetup setup, final InstantSource clock, final LongSupplier nanoClock) {
    this(setup, clock, nanoClock, new SimpleMeterRegistry());
  }

  GatewayMailbox(
      final GatewayMailboxSetup setup,
      final InstantSource clock,
      final LongSupplier nanoClock,
      final MeterRegistry registry) {
    this.setup = setup;
    this.registry = registry;
    final var store = new MeteredMailboxStore(setup.store(), registry);
    leaderSource = leaderSource(setup, store, nanoClock, registry);
    sender = new InboxSender(store, setup.gatewayId(), setup.executor(), clock);
    awaiter = new ResponseAwaiter(store, new GatewayId(setup.gatewayId()), clock);
    gauges =
        List.of(
            Gauge.builder(PENDING_REQUESTS, awaiter, ResponseAwaiter::pendingCount)
                .description("The requests in the mailbox that wait for a response")
                .register(registry),
            Gauge.builder(KNOWN_LEADERS, this, mailbox -> mailbox.leaders.size())
                .description("The partitions for which the gateway knows a leader in the mailbox")
                .register(registry));
    pollExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "gateway-mailbox-responses");
              thread.setDaemon(true);
              return thread;
            });
    pollLoop =
        new PollLoop(
            "gateway-mailbox-responses",
            Scheduler.of(pollExecutor),
            new AdaptiveBackoff(setup.config().minPollInterval(), setup.config().maxPollInterval()),
            () -> awaiter.pollOnce() > 0);
    // the leaders are read on a thread of their own, so a slow read does not delay the responses
    leaderExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final var thread = new Thread(runnable, "gateway-mailbox-leaders");
              thread.setDaemon(true);
              return thread;
            });
    leaderLoop =
        new PollLoop(
            "gateway-mailbox-leaders",
            Scheduler.of(leaderExecutor),
            new AdaptiveBackoff(LEADER_REFRESH_INTERVAL, LEADER_REFRESH_INTERVAL),
            () -> {
              refreshLeaders();
              return false;
            });
  }

  /**
   * In lease mode the leaders are those with a live lease, so a crashed leader drops out once its
   * lease runs out. Otherwise they are the records that the leaders keep in the mailbox store.
   */
  private static Supplier<List<PartitionLeader>> leaderSource(
      final GatewayMailboxSetup setup,
      final MailboxStore store,
      final LongSupplier nanoClock,
      final MeterRegistry registry) {
    final var leases = setup.leases();
    if (leases == null) {
      return store::findLeaders;
    }
    return new LiveLeaders(
            new MeteredLeaseStore(leases.store(), registry), leases.leaseDuration(), nanoClock)
        ::read;
  }

  /** The zone of this gateway if the regions are simulated in one network, otherwise null. */
  public @Nullable String simulatedZone() {
    return setup.simulatedZone();
  }

  public void start() {
    pollLoop.start();
    leaderExecutor.execute(this::refreshLeaders);
    leaderLoop.start();
  }

  /**
   * Returns the leader that the store recorded for a partition, as of the last refresh. It does not
   * block, so the request path and the partition dispatch can call it. The copy is at most a
   * refresh interval old.
   */
  public Optional<PartitionLeader> recordedLeader(final int partitionId) {
    return Optional.ofNullable(leaders.get(partitionId));
  }

  /**
   * Reads the recorded leaders from the store now, which also happens regularly. A failure keeps
   * the copy that exists. It blocks, so it must not run on an actor thread.
   */
  public void refreshLeaders() {
    try {
      final var refreshed = new HashMap<Integer, PartitionLeader>();
      for (final var leader : leaderSource.get()) {
        refreshed.put(leader.partitionId(), leader);
      }
      leaders = Map.copyOf(refreshed);
    } catch (final MailboxStoreException e) {
      LOG.warn("Could not read the leaders of the partitions from the mailbox", e);
    }
  }

  /**
   * Sends a request to the inbox of a partition and waits for the response.
   *
   * @param payload the serialized request, as it would go over the network
   * @param timeout how long to wait for the response
   * @return the serialized response. Fails with a {@link java.util.concurrent.TimeoutException} if
   *     none comes in time, and with an {@link IllegalStateException} if the request could not be
   *     put into the inbox.
   */
  public CompletableFuture<byte[]> send(
      final int partitionId, final byte[] payload, final Duration timeout) {
    final var requestId = sender.nextRequestId();
    final var sample = Timer.start(registry);
    // wait first, so that a fast response cannot arrive before the wait has started
    final var response = awaiter.await(requestId, timeout);
    response.whenComplete(
        (ignored, error) -> sample.stop(requestTimer(partitionId, outcomeOf(error))));
    sender
        .send(partitionId, MessageKind.CLIENT_REQUEST, requestId, payload)
        .whenComplete(
            (inserted, error) -> {
              if (error != null || !Boolean.TRUE.equals(inserted)) {
                response.completeExceptionally(
                    new IllegalStateException(
                        "Could not put the request into the inbox of partition " + partitionId,
                        error));
              }
            });
    return response;
  }

  private Timer requestTimer(final int partitionId, final String outcome) {
    return Timer.builder(REQUEST_LATENCY)
        .description("The time from putting a request into the inbox to its response, or failure")
        .tag("partition", String.valueOf(partitionId))
        .tag("outcome", outcome)
        .publishPercentileHistogram()
        .register(registry);
  }

  private static String outcomeOf(final @Nullable Throwable error) {
    if (error == null) {
      return "success";
    }
    final var cause = error instanceof CompletionException ? error.getCause() : error;
    if (cause instanceof TimeoutException) {
      return "timeout";
    }
    return cause instanceof IllegalStateException ? "not_sent" : "failure";
  }

  @Override
  public void close() {
    gauges.forEach(registry::remove);
    pollLoop.close();
    leaderLoop.close();
    pollExecutor.shutdownNow();
    leaderExecutor.shutdownNow();
  }
}
