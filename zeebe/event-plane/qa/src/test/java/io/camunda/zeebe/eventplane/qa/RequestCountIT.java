/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.zeebe.eventplane.consumer.EventPlaneConsumer;
import io.camunda.zeebe.eventplane.consumer.Start;
import io.camunda.zeebe.eventplane.service.EventPlaneService;
import io.camunda.zeebe.eventplane.service.ServiceConfig;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Counts the requests the Event Plane makes of its store under steady load, with the default
 * timings, and extrapolates them to an hour: what the publisher writes per partition, and what a
 * worker of a group reads and writes, with and without the service. Writes a table to
 * target/event-plane-requests.md. A measurement more than a test: it checks only that the group
 * kept up.
 */
@ZeebeIntegration
final class RequestCountIT {

  private static final Duration WINDOW = Duration.ofSeconds(90);
  private static final int PARTITIONS = 3;
  private static final int WORKERS = 3;
  private static final Path REPORT = Path.of("target", "event-plane-requests.md");

  @TempDir private static Path buckets;
  private final Path bucket = buckets.resolve(UUID.randomUUID().toString());
  private final List<EventPlaneConsumer> workers = new ArrayList<>();
  private @Nullable EventPlaneService service;

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(false)
          .withSecondaryStorageType(SecondaryStorageType.none)
          .withUnifiedConfig(config -> config.getCluster().setPartitionCount(PARTITIONS))
          .withProperty(EventPlaneBrokers.PREFIX + "enabled", true)
          .withProperty(EventPlaneBrokers.PREFIX + "store.type", "filesystem")
          .withProperty(EventPlaneBrokers.PREFIX + "store.path", bucket.toString());

  @AfterEach
  void tearDown() {
    workers.forEach(EventPlaneConsumer::close);
    if (service != null) {
      service.close();
    }
  }

  @Test
  void shouldCountTheRequestsWithTheService() throws Exception {
    service =
        new EventPlaneService(
            new FileSystemStore(bucket), new ServiceConfig(new InetSocketAddress("localhost", 0)));
    service.start();
    final var url = URI.create("http://localhost:" + service.port());
    broker.withProperty(EventPlaneBrokers.PREFIX + "serviceUrl", url.toString());
    measure("with the service", url);
  }

  @Test
  void shouldCountTheRequestsWithoutTheService() throws Exception {
    measure("without the service", null);
  }

  private void measure(final String setup, final @Nullable URI serviceUrl) throws Exception {
    // given a broker with the default cut interval, and a group with the default timings
    broker.start().awaitCompleteTopology(1, PARTITIONS, 1, Duration.ofSeconds(60));
    EventPlaneBrokers.publishMessages(broker, 30);
    await("a log for every partition")
        .timeout(Duration.ofSeconds(60))
        .until(() -> Bucket.logs(new FileSystemStore(bucket)).size() == PARTITIONS);
    final var stores = new ArrayList<CountingStore>();
    final var delivered = new AtomicLong();
    for (int i = 0; i < WORKERS; i++) {
      final var store = new CountingStore(new FileSystemStore(bucket));
      stores.add(store);
      final var builder =
          EventPlaneConsumer.builder()
              .store(store)
              .group("g")
              .workerId("w-" + i)
              .subscribe("*", "*")
              .start(Start.LATEST)
              .handler((log, record) -> delivered.incrementAndGet());
      if (serviceUrl != null) {
        builder.service(serviceUrl);
      }
      final var worker = builder.build();
      worker.start();
      workers.add(worker);
    }

    // when the load runs for the window
    final var start = Instant.now();
    final var load = steadyLoad();
    Thread.sleep(WINDOW.toMillis());
    load.set(false);
    final var counts = new TreeMap<String, Long>();
    stores.forEach(store -> store.counts().forEach((key, n) -> counts.merge(key, n, Long::sum)));
    final long segments = segmentsSince(start);

    // then
    assertThat(delivered.get()).isPositive();
    report(setup, counts, segments);
  }

  private AtomicBoolean steadyLoad() {
    final var running = new AtomicBoolean(true);
    Thread.ofPlatform()
        .daemon()
        .start(
            () -> {
              try (final var client = broker.newClientBuilder().build()) {
                for (int i = 0; running.get(); i++) {
                  client
                      .newPublishMessageCommand()
                      .messageName("load")
                      .correlationKey("key-" + i)
                      .send()
                      .join();
                  Thread.sleep(50);
                }
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            });
    return running;
  }

  /** The segments the publishers uploaded since, by their ULIDs' time. */
  private long segmentsSince(final Instant start) throws IOException {
    final var store = new FileSystemStore(bucket);
    long count = 0;
    for (final var log : Bucket.logs(store)) {
      final var prefix = ObjectNames.logPrefix(log.clusterId(), log.tenantId(), log.partition());
      for (final var directory : store.listPrefixes(prefix)) {
        for (final var object : store.list(directory, null, Integer.MAX_VALUE)) {
          if (object.key().endsWith(".seg")
              && ulidTime(ObjectNames.parseSegment(object.key()).ulid()).isAfter(start)) {
            count++;
          }
        }
      }
    }
    return count;
  }

  private static Instant ulidTime(final String ulid) {
    final var alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    long millis = 0;
    for (int i = 0; i < 10; i++) {
      millis = millis * 32 + alphabet.indexOf(Character.toUpperCase(ulid.charAt(i)));
    }
    return Instant.ofEpochMilli(millis);
  }

  private static void report(
      final String setup, final Map<String, Long> counts, final long segments) throws IOException {
    final double perHour = 3600.0 / WINDOW.toSeconds();
    final var table = new StringBuilder();
    table
        .append("\n## Requests per hour, ")
        .append(setup)
        .append("\n\n")
        .append(PARTITIONS)
        .append(" partitions, ")
        .append(WORKERS)
        .append(" workers, default timings, ~20 commands/s, measured over ")
        .append(WINDOW.toSeconds())
        .append(" s.\n\n| Who | Request | For | Per hour |\n|---|---|---|---:|\n")
        .append(
            "| publisher, per partition | PUT | segments | %.0f |\n"
                .formatted(segments * perHour / PARTITIONS));
    counts.forEach(
        (key, count) -> {
          final var parts = key.split(" ", 2);
          table.append(
              "| worker, per worker | %s | %s | %.0f |\n"
                  .formatted(parts[0], parts[1], count * perHour / WORKERS));
        });
    Files.writeString(
        REPORT,
        table,
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND);
    System.out.println(table);
  }
}
