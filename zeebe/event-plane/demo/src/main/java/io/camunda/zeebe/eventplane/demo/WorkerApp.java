/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.demo;

import io.camunda.zeebe.eventplane.consumer.ConsumerListener;
import io.camunda.zeebe.eventplane.consumer.Cursor;
import io.camunda.zeebe.eventplane.consumer.EventPlaneConsumer;
import io.camunda.zeebe.eventplane.consumer.LogId;
import io.camunda.zeebe.eventplane.consumer.Start;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.service.ServiceSettings;
import io.camunda.zeebe.eventplane.store.ObjectStores;
import io.camunda.zeebe.eventplane.store.Ulid;
import io.camunda.zeebe.protocol.record.Record;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * A worker of a consumer group for trying the Event Plane out: it counts what it reads per log, and
 * prints every ten seconds how many records came, how many came again, and how old the newest one
 * was when it arrived. Takes the store's settings as the service does (see {@link
 * ServiceSettings}), and EVENTPLANE_GROUP, EVENTPLANE_WORKER, EVENTPLANE_SERVICE_URL and
 * EVENTPLANE_START (earliest or latest).
 */
public final class WorkerApp {

  private WorkerApp() {}

  public static void main(final String[] args) throws Exception {
    final var env = System.getenv();
    final var store = ObjectStores.open(ServiceSettings.fromEnvironment().store());
    final var worker = env.getOrDefault("EVENTPLANE_WORKER", "w-" + Ulid.next());
    final var stats = new Stats(worker);
    final var builder =
        EventPlaneConsumer.builder()
            .store(store)
            .group(env.getOrDefault("EVENTPLANE_GROUP", "demo"))
            .workerId(worker)
            .subscribe("*", "*")
            .start(
                Start.valueOf(
                    env.getOrDefault("EVENTPLANE_START", "earliest").toUpperCase(Locale.ROOT)))
            .handler(stats::record)
            .listener(stats);
    final var service = env.get("EVENTPLANE_SERVICE_URL");
    if (service != null && !service.isBlank()) {
      builder.service(URI.create(service));
    }
    final var stopped = new CountDownLatch(1);
    try (final var consumer = builder.build()) {
      Runtime.getRuntime().addShutdownHook(new Thread(stopped::countDown));
      consumer.start();
      while (!stopped.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
        stats.print();
      }
    } finally {
      store.close();
    }
  }

  /** What a worker read, per log. */
  private static final class Stats implements ConsumerListener {

    private final String worker;
    private final Map<String, Log> logs = new TreeMap<>();

    Stats(final String worker) {
      this.worker = worker;
    }

    synchronized void record(final LogId log, final Record<?> record) {
      final var stats = logs.computeIfAbsent(log.toString(), ignored -> new Log());
      if (record.getPosition() <= stats.lastPosition) {
        stats.again++;
      } else {
        stats.lastPosition = record.getPosition();
        stats.records++;
        stats.newest = record.getTimestamp();
        stats.arrived = System.currentTimeMillis();
      }
    }

    synchronized void print() {
      final var line = new StringBuilder(worker).append(':');
      if (logs.isEmpty()) {
        line.append(" nothing read yet");
      }
      logs.forEach(
          (log, stats) ->
              line.append(
                  " | %s: %d records (+%d), %d again, at position %d, newest %d ms old on arrival"
                      .formatted(
                          log.substring(log.indexOf('/') + 1),
                          stats.records,
                          stats.records - stats.printed,
                          stats.again,
                          stats.lastPosition,
                          stats.arrived - stats.newest)));
      logs.values().forEach(stats -> stats.printed = stats.records);
      System.out.println(line);
    }

    @Override
    public void onGap(final LogId log, final long fromIndex, final long toIndex) {
      System.out.printf("%s: GAP in %s from index %d to %d%n", worker, log, fromIndex, toIndex);
    }

    @Override
    public void onHistoryChange(
        final LogId log, final Cursor old, final Base shared, final UUID current) {
      System.out.printf(
          "%s: HISTORY CHANGED in %s: read up to index %d, the histories share up to index %d,"
              + " reading on in %s%n",
          worker, log, old.index(), shared.index(), current);
    }

    private static final class Log {
      private long records;
      private long printed;
      private long again;
      private long lastPosition;
      private long newest;
      private long arrived;
    }
  }
}
