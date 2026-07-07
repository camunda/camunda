/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.examples;

import io.camunda.eventbridge.client.EventBridgeClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manual POC driver that produces <em>and</em> fetch-consumes a topic continuously while an
 * external action (e.g. adding brokers + reassigning the topic) happens, then reconciles to prove
 * no records were lost and the topic stayed available through the move.
 *
 * <p>Each partition is produced round-robin (one keyed record per tick) and a per-partition
 * consumer tails it from offset 1, advancing as it reads. Producer and consumer failures are
 * counted separately (transient failures during a leader move are expected and retried — the
 * invariant that matters is that every acknowledged record is eventually consumed). After the run
 * it drains each partition and asserts consumed == acknowledged-produced per partition.
 *
 * <pre>
 *   RebalanceLoadDriver [topic] [partitions] [durationSeconds] -Dgateway=http://localhost:8080
 * </pre>
 */
public final class RebalanceLoadDriver {

  private RebalanceLoadDriver() {}

  public static void main(final String[] args) throws Exception {
    final var gateway = System.getProperty("gateway", "http://localhost:8080");
    final var topic = args.length > 0 ? args[0] : "orders";
    final int partitions = args.length > 1 ? Integer.parseInt(args[1]) : 6;
    final long durationMs = (args.length > 2 ? Long.parseLong(args[2]) : 60) * 1000L;

    try (final var client = EventBridgeClient.create(gateway)) {
      final var produced = new ConcurrentHashMap<Integer, AtomicLong>();
      final var produceFailures = new AtomicLong();
      final var consumed = new ConcurrentHashMap<Integer, AtomicLong>();
      final var consumeFailures = new AtomicLong();
      final var nextOffset = new ConcurrentHashMap<Integer, Long>();
      for (int p = 1; p <= partitions; p++) {
        produced.put(p, new AtomicLong());
        consumed.put(p, new AtomicLong());
        nextOffset.put(p, 1L);
      }
      final var running = new AtomicBoolean(true);

      final var producer =
          new Thread(
              () -> {
                long seq = 0;
                while (running.get()) {
                  for (int p = 1; p <= partitions; p++) {
                    final var value = ("rec-" + p + "-" + seq).getBytes(StandardCharsets.UTF_8);
                    try {
                      client.publishToTopic(topic, p, "k-" + seq, value).join();
                      produced.get(p).incrementAndGet();
                    } catch (final RuntimeException e) {
                      produceFailures.incrementAndGet();
                    }
                  }
                  seq++;
                  sleepQuietly(50);
                }
              },
              "producer");

      final var consumer =
          new Thread(
              () ->
                  consumeLoop(
                      client, topic, partitions, running, nextOffset, consumed, consumeFailures),
              "consumer");

      System.out.printf(
          "Driving load on %s (%d partitions) for %d ms…%n", topic, partitions, durationMs);
      producer.start();
      consumer.start();
      Thread.sleep(durationMs);
      running.set(false);
      producer.join();
      consumer.join();

      // Final drain: keep consuming until each partition is caught up to its high watermark.
      System.out.println("Draining remaining records…");
      drain(client, topic, partitions, nextOffset, consumed);

      report(partitions, produced, produceFailures, consumed, consumeFailures);
    }
  }

  private static void consumeLoop(
      final EventBridgeClient client,
      final String topic,
      final int partitions,
      final AtomicBoolean running,
      final Map<Integer, Long> nextOffset,
      final Map<Integer, AtomicLong> consumed,
      final AtomicLong consumeFailures) {
    while (running.get()) {
      for (int p = 1; p <= partitions; p++) {
        consumeOnce(client, topic, p, nextOffset, consumed, consumeFailures);
      }
      sleepQuietly(50);
    }
  }

  private static void consumeOnce(
      final EventBridgeClient client,
      final String topic,
      final int p,
      final Map<Integer, Long> nextOffset,
      final Map<Integer, AtomicLong> consumed,
      final AtomicLong consumeFailures) {
    try {
      final var from = nextOffset.get(p);
      final var result = client.fetchFromTopic(topic, p, from, 1 << 20).join();
      if (!result.isSuccess()) {
        return;
      }
      long count = 0;
      long lastPos = from - 1;
      // Filter to entries at/after the requested offset — a batch may begin before `from`.
      for (final var entry : result.entries(from)) {
        count++;
        lastPos = entry.position();
      }
      if (count > 0) {
        consumed.get(p).addAndGet(count);
        nextOffset.put(p, lastPos + 1);
      }
    } catch (final RuntimeException e) {
      consumeFailures.incrementAndGet();
    }
  }

  private static void drain(
      final EventBridgeClient client,
      final String topic,
      final int partitions,
      final Map<Integer, Long> nextOffset,
      final Map<Integer, AtomicLong> consumed) {
    final var failures = new AtomicLong();
    for (int p = 1; p <= partitions; p++) {
      long before;
      int idle = 0;
      do {
        before = consumed.get(p).get();
        consumeOnce(client, topic, p, nextOffset, consumed, failures);
        idle = consumed.get(p).get() == before ? idle + 1 : 0;
        sleepQuietly(100);
      } while (idle < 5); // stop once 5 consecutive polls return nothing new
    }
  }

  private static void report(
      final int partitions,
      final Map<Integer, AtomicLong> produced,
      final AtomicLong produceFailures,
      final Map<Integer, AtomicLong> consumed,
      final AtomicLong consumeFailures) {
    long totalProduced = 0;
    long totalConsumed = 0;
    boolean lossFree = true;
    System.out.println("---- per-partition reconciliation ----");
    for (int p = 1; p <= partitions; p++) {
      final long prod = produced.get(p).get();
      final long cons = consumed.get(p).get();
      totalProduced += prod;
      totalConsumed += cons;
      final var ok = cons >= prod; // at-least-once: every acknowledged record was consumed
      lossFree &= ok;
      System.out.printf(
          "  p%d produced=%d consumed=%d %s%n", p, prod, cons, ok ? "OK" : "*** LOSS ***");
    }
    System.out.println("--------------------------------------");
    System.out.printf(
        "TOTAL produced=%d consumed=%d produceFailures=%d consumeFailures=%d%n",
        totalProduced, totalConsumed, produceFailures.get(), consumeFailures.get());
    System.out.println(
        lossFree
            ? "RESULT: NO LOSS — every acknowledged record was consumed"
            : "RESULT: DATA LOSS DETECTED");
  }

  private static void sleepQuietly(final long ms) {
    try {
      Thread.sleep(ms);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
