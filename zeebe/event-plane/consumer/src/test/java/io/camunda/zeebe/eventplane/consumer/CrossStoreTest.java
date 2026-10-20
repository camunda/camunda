/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.eventplane.history.HistoryIds;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import io.camunda.zeebe.eventplane.store.ObjectStores;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One group reads the logs of clusters that write to stores of their own. */
final class CrossStoreTest {

  private static final LogId EAST = new LogId("east", "default", 1);
  private static final LogId WEST = new LogId("west", "default", 1);

  @TempDir Path directory;
  private ObjectStore home;
  private ObjectStore eastStore;
  private ObjectStore westStore;
  private final List<TestJournal> journals = new ArrayList<>();
  private final List<ObjectStore> opened = new ArrayList<>();
  private final List<EventPlaneConsumer> workers = new ArrayList<>();
  private final Map<LogId, Set<Long>> delivered = new ConcurrentHashMap<>();

  @BeforeEach
  void setUp() throws IOException {
    home = new FileSystemStore(directory.resolve("home"));
    eastStore = new FileSystemStore(directory.resolve("east"));
    westStore = new FileSystemStore(directory.resolve("west"));
  }

  @AfterEach
  void tearDown() throws IOException {
    workers.forEach(EventPlaneConsumer::close);
    for (final var store : opened) {
      store.close();
    }
    journals.forEach(TestJournal::close);
  }

  @Test
  void shouldReadEveryClusterFromItsOwnStore() throws IOException {
    // given
    final long eastEnd = upload(eastStore, EAST);
    final long westEnd = upload(westStore, WEST);

    // when
    final Consumer<EventPlaneConsumer.Builder> stores =
        builder -> builder.logStore("east", eastStore).logStore("west", westStore);
    worker("w-1", stores);
    worker("w-2", stores);

    // then
    assertEveryLogReadFromItsOwnStore(eastEnd, westEnd);
  }

  @Test
  void shouldReadEveryClusterFromTheStoreThatTheServiceNames() throws Exception {
    // given
    final long eastEnd = upload(eastStore, EAST);
    final long westEnd = upload(westStore, WEST);

    try (final var service =
        StubService.start()
            .clusters(
                Map.of(
                    "east", filesystemJson(directory.resolve("east")),
                    "west", filesystemJson(directory.resolve("west"))))) {
      // when
      final var logStores = new HashMap<String, ObjectStore>();
      for (final var cluster : ServiceClusters.fetch(service.uri()).entrySet()) {
        final var store = ObjectStores.open(cluster.getValue());
        opened.add(store);
        logStores.put(cluster.getKey(), store);
      }
      worker("w-1", builder -> builder.logStores(logStores));
      worker("w-2", builder -> builder.logStores(logStores));

      // then
      assertEveryLogReadFromItsOwnStore(eastEnd, westEnd);
    }
  }

  private void assertEveryLogReadFromItsOwnStore(final long eastEnd, final long westEnd)
      throws IOException {
    await()
        .untilAsserted(
            () -> {
              assertThat(delivered.get(EAST)).containsAll(range(eastEnd));
              assertThat(delivered.get(WEST)).containsAll(range(westEnd));
            });
    await("the cursors at home")
        .untilAsserted(
            () -> {
              for (final var log : List.of(EAST, WEST)) {
                assertThat(home.get(cursorKey(log))).isPresent();
              }
            });
    assertThat(eastStore.list(ObjectNames.groupsPrefix(), null, 10)).isEmpty();
    assertThat(westStore.list(ObjectNames.groupsPrefix(), null, 10)).isEmpty();
  }

  private void worker(final String id, final Consumer<EventPlaneConsumer.Builder> logStores) {
    final var builder =
        EventPlaneConsumer.builder()
            .store(home)
            .group("g")
            .workerId(id)
            .subscribe("*", "*")
            .start(Start.EARLIEST)
            .handler(
                (log, record) ->
                    delivered
                        .computeIfAbsent(log, ignored -> new ConcurrentSkipListSet<>())
                        .add(record.getPosition()))
            .heartbeatInterval(Duration.ofMillis(50))
            .lease(Duration.ofSeconds(1))
            .cursorInterval(Duration.ofMillis(100))
            .listingInterval(Duration.ofMillis(100));
    logStores.accept(builder);
    final var worker = builder.build();
    workers.add(worker);
    worker.start();
  }

  private static String filesystemJson(final Path root) {
    return "{\"type\":\"filesystem\",\"path\":\"%s\"}".formatted(root);
  }

  /** Uploads a log's first segments to a store, and returns the last record's position. */
  private long upload(final ObjectStore store, final LogId log) throws IOException {
    final var journal = new TestJournal(directory.resolve("journal-" + log.cluster()));
    journals.add(journal);
    final var testLog = new TestLog(store, log);
    final var history = HistoryIds.initial(log.cluster(), log.tenant());
    testLog.history(history, Base.NOTHING, Instant.parse("2026-10-20T12:00:00Z"));
    journal.initial();
    for (int i = 0; i < 3; i++) {
      testLog.upload(history, journal.jobEntries(4, 3));
    }
    return journal.lastPosition();
  }

  private static String cursorKey(final LogId log) {
    return ObjectNames.cursor("g", log.cluster(), log.tenant(), log.partition());
  }

  private static List<Long> range(final long to) {
    return LongStream.rangeClosed(1, to).boxed().toList();
  }
}
