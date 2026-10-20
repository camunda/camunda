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
import io.camunda.zeebe.eventplane.consumer.LogId;
import io.camunda.zeebe.eventplane.consumer.Start;
import io.camunda.zeebe.eventplane.qa.Bucket.LogRef;
import io.camunda.zeebe.eventplane.service.EventPlaneService;
import io.camunda.zeebe.eventplane.service.ServiceConfig;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A consumer group reads every record a broker publishes, at least once and in order per log, while
 * workers come and go, and the service passes the uploads on to them.
 */
@ZeebeIntegration
final class GroupIT {

  private static final Duration NO_LISTING = Duration.ofMinutes(10);
  private static final Pattern LAG = Pattern.compile("\"lag\":(\\d+)");

  @TempDir private static Path buckets;
  private final Path bucket = buckets.resolve(UUID.randomUUID().toString());
  private final FileSystemStore store = new FileSystemStore(bucket);
  private final Deliveries deliveries = new Deliveries();
  private final List<EventPlaneConsumer> workers = new ArrayList<>();
  private EventPlaneService service;

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      EventPlaneBrokers.withEventPlane(new TestStandaloneBroker(), bucket)
          .withRecordingExporter(false)
          .withSecondaryStorageType(SecondaryStorageType.none)
          .withUnifiedConfig(config -> config.getCluster().setPartitionCount(3));

  @BeforeEach
  void setUp() throws Exception {
    service =
        new EventPlaneService(store, new ServiceConfig(new InetSocketAddress("localhost", 0)));
    service.start();
    broker
        .withProperty(EventPlaneBrokers.PREFIX + "serviceUrl", serviceUrl().toString())
        .start()
        .awaitCompleteTopology(1, 3, 1, Duration.ofSeconds(60));
  }

  @AfterEach
  void tearDown() {
    workers.forEach(EventPlaneConsumer::close);
    service.close();
  }

  @Test
  void shouldDeliverEveryRecordWhileWorkersComeAndGo() throws Exception {
    // given
    final var first = startWorker("w-a", Duration.ofSeconds(1));
    startWorker("w-b", Duration.ofSeconds(1));
    startWorker("w-c", Duration.ofSeconds(1));
    EventPlaneBrokers.publishMessages(broker, 60);

    // when
    first.close();
    startWorker("w-d", Duration.ofSeconds(1));
    EventPlaneBrokers.publishMessages(broker, 60);

    // then
    awaitEveryRecordDelivered();
    assertThat(deliveries.firstArrivalsInOrder()).isTrue();
    await("the service sees the group caught up")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var view = groupView();
              assertThat(view).contains("\"worker\":\"w-d\"").doesNotContain("\"worker\":\"w-a\"");
              assertThat(LAG.matcher(view).results().map(lag -> lag.group(1)))
                  .hasSize(3)
                  .containsOnly("0");
            });
  }

  @Test
  void shouldWakeWorkersThroughTheService() throws Exception {
    // given workers that find the logs once, and would list the bucket again only in ten minutes
    awaitEveryLogInTheBucket();
    startWorker("w-a", NO_LISTING);
    startWorker("w-b", NO_LISTING);

    // when
    EventPlaneBrokers.publishMessages(broker, 30);

    // then
    awaitEveryRecordDelivered();
  }

  @Test
  void shouldWakeWorkersAfterTheServiceRestarts() throws Exception {
    // given
    awaitEveryLogInTheBucket();
    startWorker("w-a", NO_LISTING);
    EventPlaneBrokers.publishMessages(broker, 10);
    awaitEveryRecordDelivered();

    // when
    final int port = service.port();
    service.close();
    service =
        new EventPlaneService(store, new ServiceConfig(new InetSocketAddress("localhost", port)));
    service.start();
    EventPlaneBrokers.publishMessages(broker, 10);

    // then
    awaitEveryRecordDelivered();
  }

  private EventPlaneConsumer startWorker(final String id, final Duration listing) {
    final var worker =
        EventPlaneConsumer.builder()
            .store(store)
            .group("g")
            .workerId(id)
            .subscribe("*", "*")
            .start(Start.EARLIEST)
            .handler(deliveries::add)
            .service(serviceUrl())
            .heartbeatInterval(Duration.ofMillis(200))
            .lease(Duration.ofSeconds(2))
            .cursorInterval(Duration.ofMillis(500))
            .listingInterval(listing)
            .build();
    worker.start();
    workers.add(worker);
    return worker;
  }

  /** Every record of every log in the bucket, once the uploads have settled, reached a worker. */
  private void awaitEveryRecordDelivered() {
    await("every record in the bucket delivered")
        .timeout(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              final var logs = Bucket.logs(store);
              assertThat(logs).isNotEmpty();
              for (final LogRef log : logs) {
                final var read = Bucket.readLog(store, log);
                final var expected =
                    LongStream.rangeClosed(1, read.lastPosition()).boxed().toList();
                assertThat(deliveries.positions(log)).containsAll(expected);
              }
            });
  }

  /** A log appears with its first upload; workers find logs only when they list. */
  private void awaitEveryLogInTheBucket() {
    EventPlaneBrokers.publishMessages(broker, 6);
    await("a log for every partition")
        .timeout(Duration.ofSeconds(30))
        .until(() -> Bucket.logs(store).size() == 3);
  }

  private URI serviceUrl() {
    return URI.create("http://localhost:" + service.port());
  }

  private String groupView() throws Exception {
    try (final var client = HttpClient.newHttpClient()) {
      return client
          .send(
              HttpRequest.newBuilder(serviceUrl().resolve("/v1/groups/g")).build(),
              BodyHandlers.ofString())
          .body();
    }
  }

  /** What the workers delivered, per log, in arrival order. */
  private static final class Deliveries {

    private final Map<String, List<Long>> arrivals = new ConcurrentHashMap<>();

    void add(final LogId log, final Record<?> record) {
      final var list =
          arrivals.computeIfAbsent(
              log.toString(), key -> Collections.synchronizedList(new ArrayList<>()));
      list.add(record.getPosition());
    }

    Set<Long> positions(final LogRef log) {
      final var list =
          arrivals.getOrDefault(
              log.clusterId() + "/" + log.tenantId() + "/" + log.partition(), List.of());
      synchronized (list) {
        return new TreeSet<>(list);
      }
    }

    /** The first time each record arrived, it came after every record before it in its log. */
    boolean firstArrivalsInOrder() {
      for (final var list : arrivals.values()) {
        synchronized (list) {
          final var seen = new TreeSet<Long>();
          long last = 0;
          for (final long position : list) {
            if (seen.add(position)) {
              if (position < last) {
                return false;
              }
              last = position;
            }
          }
        }
      }
      return true;
    }
  }
}
