/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.notification.UploadNotice;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A worker of a consumer group, which reads the logs of the group's subscription together with the
 * group's other workers. They coordinate only through the store, with plain PUTs:
 *
 * <ul>
 *   <li>Every heartbeat interval, the worker rewrites its heartbeat with the beat counting up, and
 *       reads everyone else's. A worker is live while its beat keeps changing, timed on this
 *       worker's own clock, and dead once it hasn't changed for a lease.
 *   <li>Each log belongs to the live worker with the highest rendezvous hash of worker and log.
 *   <li>A worker that comes to own a log reads its cursor. It starts at once if there is none, if
 *       the cursor names itself, or if it names a worker that is dead; else it waits a lease, so
 *       that the previous holder sees the change too and stops, and reads the cursor again.
 *   <li>A worker that no longer owns a log stops reading it at an entry boundary and writes its
 *       final cursor. The holder writes the cursor every cursor interval while it moves.
 * </ul>
 *
 * Delivery is at least once: around a handover, records can come twice, but none is skipped, and
 * the first arrival of each record of a log comes in position order. With a service, the worker
 * listens for uploads of the logs it holds; without, it lists more often.
 *
 * <p>The group's objects live in one store, its home. The logs can live elsewhere: each cluster
 * writes to a store of its own, which {@link Builder#logStore} names. The worker then finds logs in
 * every store, and reads each one from its cluster's store.
 */
public final class EventPlaneConsumer implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(EventPlaneConsumer.class);
  private static final int PAGE_SIZE = 1000;

  private final ObjectStore store;
  private final Map<String, ObjectStore> logStores;
  private final List<ObjectStore> stores;
  private volatile Map<LogId, ObjectStore> logLocations = Map.of();
  private final String group;
  private final String workerId;
  private final List<Subscription> subscription;
  private final Start start;
  private final RecordHandler handler;
  private final ConsumerListener listener;
  private final @Nullable URI service;
  private final Duration heartbeatInterval;
  private final Duration lease;
  private final Duration cursorInterval;
  private final Duration listingInterval;
  private final Duration holeGrace;
  private final MonotonicClock clock;
  private final Liveness liveness;
  private final String heartbeatKey;

  /** The logs this worker reads; the coordinator changes it, notifications look up in it. */
  private final Map<LogId, HeldLog> held = new ConcurrentHashMap<>();

  // Only the coordinator thread touches these, after start() and before close() wait for it.
  private final Map<LogId, HeldLog> releasing = new HashMap<>();
  private final Map<LogId, Long> pending = new HashMap<>();
  private Set<LogId> logs = Set.of();
  private long beat;
  private long lastBeat;
  private long lastDiscovery;
  private long lastCursorWrite;
  private boolean discovered;

  // Guarded by this.
  private @Nullable ScheduledExecutorService coordinator;
  private @Nullable Notifications notifications;
  private boolean closed;

  private EventPlaneConsumer(final Builder builder) {
    store = Objects.requireNonNull(builder.store, "store");
    logStores = Map.copyOf(builder.logStores);
    final var distinct = new ArrayList<ObjectStore>();
    distinct.add(store);
    logStores.values().stream().filter(other -> !distinct.contains(other)).forEach(distinct::add);
    stores = List.copyOf(distinct);
    group = Objects.requireNonNull(builder.group, "group");
    workerId = builder.workerId != null ? builder.workerId : "w-" + Ulid.next();
    subscription = List.copyOf(builder.subscription);
    if (subscription.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a subscription of at least one cluster and tenant, but got none");
    }
    start = builder.start;
    handler = Objects.requireNonNull(builder.handler, "handler");
    listener = builder.listener;
    service = builder.service;
    heartbeatInterval = builder.heartbeatInterval;
    lease = builder.lease;
    cursorInterval = builder.cursorInterval;
    listingInterval =
        builder.listingInterval != null
            ? builder.listingInterval
            : service != null ? Duration.ofSeconds(60) : Duration.ofSeconds(1);
    holeGrace = builder.holeGrace;
    clock = builder.clock;
    liveness = new Liveness(lease);
    heartbeatKey = ObjectNames.worker(group, workerId);
    lastBeat = clock.nanoTime();
  }

  public static Builder builder() {
    return new Builder();
  }

  public String workerId() {
    return workerId;
  }

  public String group() {
    return group;
  }

  /**
   * Starts the worker's coordinator, which writes the first heartbeat, reads the others', and takes
   * on the logs that it owns.
   *
   * @throws IllegalStateException if the worker was started before
   */
  public synchronized void start() {
    if (coordinator != null || closed) {
      throw new IllegalStateException(
          "Expected worker %s to start once, but it was started or closed before"
              .formatted(workerId));
    }
    if (service != null) {
      notifications =
          new Notifications(service, this::onUpload, this::wakeAll, Duration.ofSeconds(1));
    }
    final var executor =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("event-plane-coordinator-" + workerId).factory());
    coordinator = executor;
    executor.scheduleWithFixedDelay(
        this::round, 0, heartbeatInterval.toNanos(), TimeUnit.NANOSECONDS);
    LOG.info("Worker {} of group {} started", workerId, group);
  }

  /**
   * Stops the worker: its readers stop at an entry boundary and write their final cursors, and its
   * heartbeat is deleted, so the other workers take its logs on at once.
   */
  @Override
  public void close() {
    final ScheduledExecutorService executor;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      executor = coordinator;
      if (notifications != null) {
        notifications.close();
      }
    }
    if (executor == null) {
      return;
    }
    stop(executor);
    held.values().forEach(HeldLog::release);
    releasing.putAll(held);
    held.clear();
    join(releasing.values());
    try {
      store.delete(heartbeatKey);
    } catch (final IOException e) {
      LOG.warn("Failed to delete the heartbeat of worker {}", workerId, e);
    }
    LOG.info("Worker {} of group {} closed", workerId, group);
  }

  /**
   * Stops the worker as if it was killed: its threads stop, but it writes no cursor and leaves its
   * heartbeat, so the others take its logs on only once its lease has run out.
   */
  void halt() {
    final ScheduledExecutorService executor;
    synchronized (this) {
      closed = true;
      executor = coordinator;
      if (notifications != null) {
        notifications.close();
      }
    }
    if (executor != null) {
      stop(executor);
    }
    held.values().forEach(HeldLog::halt);
    join(held.values());
  }

  /** The logs this worker reads now. */
  Set<LogId> heldLogs() {
    return Set.copyOf(held.keySet());
  }

  private void onUpload(final UploadNotice notice) {
    final var log = new LogId(notice.clusterId(), notice.tenantId(), notice.partition());
    final var heldLog = held.get(log);
    if (heldLog != null) {
      heldLog.wake();
    }
  }

  /** Reads every held log at once, as after a stream of notifications opened again. */
  private void wakeAll() {
    held.values().forEach(HeldLog::wake);
  }

  /** One round of the coordinator: heartbeat, liveness, logs, assignment and cursors. */
  private void round() {
    try {
      final long now = clock.nanoTime();
      heartbeat(now);
      observe(now);
      if (now - lastBeat >= lease.toNanos()) {
        LOG.warn(
            "Worker {} couldn't write its heartbeat for a lease; it stops reading its logs",
            workerId);
        List.copyOf(held.keySet()).forEach(log -> release(log, held.get(log)));
        pending.clear();
      } else {
        discover(now);
        assign(now);
      }
      final boolean due = now - lastCursorWrite >= cursorInterval.toNanos();
      if (due) {
        lastCursorWrite = now;
      }
      writeCursors(due);
      releasing.values().removeIf(HeldLog::isDone);
      final Notifications listening;
      synchronized (this) {
        listening = notifications;
      }
      if (listening != null) {
        listening.subscribe(held.keySet());
      }
    } catch (final RuntimeException e) {
      LOG.warn("Worker {} failed a round of coordination; trying again", workerId, e);
    }
  }

  private void heartbeat(final long now) {
    try {
      store.put(heartbeatKey, new Heartbeat(workerId, ++beat).toJson());
      lastBeat = now;
    } catch (final IOException e) {
      LOG.warn("Worker {} failed to write its heartbeat", workerId, e);
    }
  }

  /** Lists the group's heartbeats, and reads each other worker's. */
  private void observe(final long now) {
    final var prefix = ObjectNames.workersPrefix(group);
    final Set<String> listed = new HashSet<>();
    final Map<String, Long> beats = new HashMap<>();
    try {
      String after = null;
      while (true) {
        final var page = store.list(prefix, after, PAGE_SIZE);
        for (final var object : page) {
          final var worker = workerOf(prefix, object.key());
          if (worker == null || worker.equals(workerId)) {
            continue;
          }
          listed.add(worker);
          try {
            store.get(object.key()).ifPresent(json -> readBeat(worker, json, beats));
          } catch (final IOException e) {
            LOG.debug("Failed to read the heartbeat of worker {}", worker, e);
          }
        }
        if (page.size() < PAGE_SIZE) {
          break;
        }
        after = page.getLast().key();
      }
    } catch (final IOException e) {
      LOG.warn("Worker {} failed to list the heartbeats of group {}", workerId, group, e);
      return;
    }
    liveness.observe(listed, beats, now);
  }

  private static void readBeat(
      final String worker, final byte[] json, final Map<String, Long> beats) {
    try {
      beats.put(worker, Heartbeat.fromJson(json).beat());
    } catch (final IOException e) {
      LOG.debug("Skipping a heartbeat of worker {} that isn't one", worker, e);
    }
  }

  private static @Nullable String workerOf(final String prefix, final String key) {
    if (!key.endsWith(".json")) {
      return null;
    }
    final var name = key.substring(prefix.length(), key.length() - ".json".length());
    try {
      return name.contains("/") ? null : ObjectNames.decode(name);
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  private void discover(final long now) {
    // a worker that knows no log yet looks every round, as for a cluster that just started
    if (discovered && !logs.isEmpty() && now - lastDiscovery < listingInterval.toNanos()) {
      return;
    }
    try {
      final var found = new HashMap<LogId, ObjectStore>();
      for (final var candidate : stores) {
        for (final var log : LogDiscovery.discover(candidate, subscription)) {
          // a cluster whose store is named is read there only
          if (logStores.getOrDefault(log.cluster(), candidate) == candidate) {
            found.putIfAbsent(log, candidate);
          }
        }
      }
      logLocations = Map.copyOf(found);
      logs = Set.copyOf(found.keySet());
      discovered = true;
      lastDiscovery = now;
    } catch (final IOException e) {
      LOG.warn("Worker {} failed to list the logs of its subscription", workerId, e);
    }
  }

  /** Takes on the logs this worker owns, and gives up those it no longer owns. */
  private void assign(final long now) {
    final var live = new HashSet<>(liveness.live(now));
    live.add(workerId);
    for (final var log : List.copyOf(held.keySet())) {
      if (!logs.contains(log) || !Rendezvous.owner(live, log).equals(workerId)) {
        release(log, held.get(log));
      }
    }
    pending.keySet().removeIf(log -> !logs.contains(log));
    for (final var log : logs) {
      if (!Rendezvous.owner(live, log).equals(workerId)) {
        pending.remove(log);
      } else if (!held.containsKey(log)) {
        takeOn(log, now);
      }
    }
  }

  private void takeOn(final LogId log, final long now) {
    final var previous = releasing.get(log);
    if (previous != null && !previous.isDone()) {
      // This worker's own reader of the log is still stopping.
      return;
    }
    final var deadline = pending.get(log);
    if (deadline != null && now - deadline < 0) {
      return;
    }
    final GroupCursor cursor;
    try {
      cursor = readCursor(log);
    } catch (final IOException e) {
      LOG.warn("Worker {} failed to read the cursor of log {}", workerId, log, e);
      return;
    }
    if (deadline != null) {
      pending.remove(log);
      startReading(log, cursor);
    } else if (cursor == null
        || cursor.worker().equals(workerId)
        || !liveness.isLive(cursor.worker(), now)) {
      startReading(log, cursor);
    } else {
      LOG.info(
          "Worker {} takes log {} over from live worker {} after a lease",
          workerId,
          log,
          cursor.worker());
      pending.put(log, now + lease.toNanos());
    }
  }

  private @Nullable GroupCursor readCursor(final LogId log) throws IOException {
    final var json =
        store.get(ObjectNames.cursor(group, log.cluster(), log.tenant(), log.partition()));
    return json.isPresent() ? GroupCursor.fromJson(json.get()) : null;
  }

  private void startReading(final LogId log, @Nullable final GroupCursor cursor) {
    final StartFrom from = cursor == null ? start : cursor.cursor();
    final var logStore =
        logLocations.getOrDefault(log, logStores.getOrDefault(log.cluster(), store));
    final var reader =
        new LogReader(logStore, log, from, listener, holeGrace, listingInterval, clock);
    final var heldLog = new HeldLog(store, group, workerId, reader, handler, listingInterval);
    releasing.remove(log);
    held.put(log, heldLog);
    heldLog.start();
    LOG.info("Worker {} reads log {} from {}", workerId, log, from);
  }

  private void release(final LogId log, @Nullable final HeldLog heldLog) {
    if (heldLog == null) {
      return;
    }
    LOG.info("Worker {} gives up log {}", workerId, log);
    held.remove(log);
    heldLog.release();
    releasing.put(log, heldLog);
  }

  /**
   * Writes the cursors that moved if they are due; else only the first cursor of a log that this
   * worker just took on, so that a worker that joins soon after sees who holds it.
   */
  private void writeCursors(final boolean due) {
    for (final var heldLog : held.values()) {
      if (!due && heldLog.hasWritten()) {
        continue;
      }
      try {
        heldLog.writeCursor(false);
      } catch (final IOException e) {
        LOG.warn("Worker {} failed to write the cursor of log {}", workerId, heldLog.log(), e);
      }
    }
  }

  private void stop(final ScheduledExecutorService executor) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(lease.toNanos(), TimeUnit.NANOSECONDS)) {
        executor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private void join(final Collection<HeldLog> readers) {
    for (final var reader : List.copyOf(readers)) {
      try {
        reader.join(lease);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  /** Builds a worker of a consumer group. */
  public static final class Builder {

    private @Nullable ObjectStore store;
    private final Map<String, ObjectStore> logStores = new LinkedHashMap<>();
    private @Nullable String group;
    private @Nullable String workerId;
    private final List<Subscription> subscription = new ArrayList<>();
    private Start start = Start.EARLIEST;
    private @Nullable RecordHandler handler;
    private ConsumerListener listener = ConsumerListener.NONE;
    private @Nullable URI service;
    private Duration heartbeatInterval = Duration.ofSeconds(5);
    private Duration lease = Duration.ofSeconds(30);
    private Duration cursorInterval = Duration.ofSeconds(10);
    private @Nullable Duration listingInterval;
    private Duration holeGrace = LogReader.DEFAULT_HOLE_GRACE;
    private MonotonicClock clock = MonotonicClock.SYSTEM;

    private Builder() {}

    /**
     * The group's home: the store with its heartbeats and cursors, and with the logs of every
     * cluster without a store of its own. Required.
     */
    public Builder store(final ObjectStore store) {
      this.store = store;
      return this;
    }

    /** The store a cluster writes its logs to, if it isn't the group's home store. */
    public Builder logStore(final String cluster, final ObjectStore store) {
      logStores.put(cluster, store);
      return this;
    }

    /** The consumer group's name. Required. */
    public Builder group(final String group) {
      this.group = group;
      return this;
    }

    /** This worker's id, unique in the group; {@code w-} and a new ULID by default. */
    public Builder workerId(final String workerId) {
      this.workerId = workerId;
      return this;
    }

    /** Adds every partition of a cluster's tenant; {@code *} for every cluster or tenant. */
    public Builder subscribe(final String cluster, final String tenant) {
      subscription.add(new Subscription(cluster, tenant));
      return this;
    }

    /** Adds logs to read. At least one is required. */
    public Builder subscription(final List<Subscription> subscription) {
      this.subscription.addAll(subscription);
      return this;
    }

    /** Where to start in a log that the group has no cursor for; {@code EARLIEST} by default. */
    public Builder start(final Start start) {
      this.start = start;
      return this;
    }

    /** Takes the records, from a thread per log. Required. */
    public Builder handler(final RecordHandler handler) {
      this.handler = handler;
      return this;
    }

    /** Hears of gaps and changed histories. */
    public Builder listener(final ConsumerListener listener) {
      this.listener = listener;
      return this;
    }

    /** The service that passes on notifications of uploads, such as {@code http://host:8080}. */
    public Builder service(final URI service) {
      this.service = service;
      return this;
    }

    /** How often the worker writes its heartbeat and reads the others'; 5 seconds by default. */
    public Builder heartbeatInterval(final Duration heartbeatInterval) {
      this.heartbeatInterval = heartbeatInterval;
      return this;
    }

    /** How long a worker whose beat doesn't change counts as live; 30 seconds by default. */
    public Builder lease(final Duration lease) {
      this.lease = lease;
      return this;
    }

    /** How often the worker writes the cursors that moved; 10 seconds by default. */
    public Builder cursorInterval(final Duration cursorInterval) {
      this.cursorInterval = cursorInterval;
      return this;
    }

    /**
     * How often a reader that has caught up lists anyway, and the worker lists the logs; 60 seconds
     * with a service by default, 1 second without.
     */
    public Builder listingInterval(final Duration listingInterval) {
      this.listingInterval = listingInterval;
      return this;
    }

    /** How long a hole without a retention marker lasts before it is a gap; 1 minute by default. */
    public Builder holeGrace(final Duration holeGrace) {
      this.holeGrace = holeGrace;
      return this;
    }

    Builder clock(final MonotonicClock clock) {
      this.clock = clock;
      return this;
    }

    public EventPlaneConsumer build() {
      return new EventPlaneConsumer(this);
    }
  }
}
