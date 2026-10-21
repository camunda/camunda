/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryIds;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.ManifestJson;
import io.camunda.zeebe.eventplane.history.RestoredHistory;
import io.camunda.zeebe.eventplane.publisher.Announcer;
import io.camunda.zeebe.eventplane.publisher.CutPolicy;
import io.camunda.zeebe.eventplane.publisher.EntrySource;
import io.camunda.zeebe.eventplane.publisher.FloorStore;
import io.camunda.zeebe.eventplane.publisher.SourcedEntry;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which history a leader publishes in, before and after a restore. */
final class PublisherServiceTest {

  private static final LogId LOG = new LogId("c", "default", 1);
  private static final UUID INITIAL = HistoryIds.initial("c", "default");
  private static final UUID OLD = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID RESTORED = UUID.fromString("00000000-0000-4000-8000-000000000002");
  private static final Instant RESTORED_AT = Instant.parse("2026-10-20T12:00:00Z");

  @TempDir private Path bucket;
  @TempDir private Path partitionDirectory;
  private FileSystemStore store;
  private @Nullable PublisherService service;

  @BeforeEach
  void setUp() {
    store = new FileSystemStore(bucket);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (service != null) {
      service.closeAsync().join();
    }
  }

  @Test
  void shouldStartTheFirstHistoryWithoutAFloor() throws IOException {
    // when
    start(null);

    // then
    assertThat(awaitPart(INITIAL)).isEqualTo(manifest(INITIAL, Instant.EPOCH, Base.NOTHING));
  }

  @Test
  void shouldGoOnInTheFloorsHistory() throws IOException {
    // given
    final var part = manifest(OLD, RESTORED_AT, new Base(INITIAL, 7, 5));
    Histories.writePart(store, part);

    // when
    start(new UploadFloor(OLD, 9, 8));

    // then
    assertThat(awaitPart(OLD)).isEqualTo(part);
  }

  @Test
  void shouldTakeTheSourceOfARestoreFromTheRestoredFloor() throws IOException {
    // given
    new RestoredHistory(RESTORED, RESTORED_AT, null, 20, 18).writeTo(partitionDirectory);

    // when
    start(new UploadFloor(OLD, 9, 8));

    // then
    assertThat(awaitPart(RESTORED))
        .isEqualTo(manifest(RESTORED, RESTORED_AT, new Base(OLD, 20, 18)));
  }

  @Test
  void shouldTakeTheSourceARestoreNames() throws IOException {
    // given
    new RestoredHistory(RESTORED, RESTORED_AT, OLD, 20, 18).writeTo(partitionDirectory);

    // when
    start(null);

    // then
    assertThat(awaitPart(RESTORED))
        .isEqualTo(manifest(RESTORED, RESTORED_AT, new Base(OLD, 20, 18)));
  }

  @Test
  void shouldTakeOverTheFirstHistoryAfterARestoreWithoutFloorIfItHasAPart() throws IOException {
    // given
    Histories.writePart(store, manifest(INITIAL, Instant.EPOCH, Base.NOTHING));
    new RestoredHistory(RESTORED, RESTORED_AT, null, 20, 18).writeTo(partitionDirectory);

    // when
    start(null);

    // then
    assertThat(awaitPart(RESTORED))
        .isEqualTo(manifest(RESTORED, RESTORED_AT, new Base(INITIAL, 20, 18)));
  }

  @Test
  void shouldTakeOverNothingAfterARestoreWithoutFloorOrPublishedHistory() throws IOException {
    // given
    new RestoredHistory(RESTORED, RESTORED_AT, null, 20, 18).writeTo(partitionDirectory);

    // when
    start(null);

    // then
    assertThat(awaitPart(RESTORED)).isEqualTo(manifest(RESTORED, RESTORED_AT, Base.NOTHING));
  }

  @Test
  void shouldKeepTheRestoredHistoryOnceTheFloorNamesIt() throws IOException {
    // given
    final var part = manifest(RESTORED, RESTORED_AT, new Base(OLD, 20, 18));
    Histories.writePart(store, part);
    new RestoredHistory(RESTORED, RESTORED_AT, null, 20, 18).writeTo(partitionDirectory);

    // when
    start(new UploadFloor(RESTORED, 30, 29));

    // then
    assertThat(awaitPart(RESTORED)).isEqualTo(part);
    assertThat(store.list(ObjectNames.historiesPrefix("c", "default"), null, 10)).hasSize(1);
  }

  private void start(final @Nullable UploadFloor floor) throws IOException {
    final var floors = FloorStore.inMemory();
    if (floor != null) {
      floors.store(floor);
    }
    service =
        new PublisherService(
            LOG,
            "c",
            partitionDirectory,
            store,
            new EmptySource(),
            floors,
            Announcer.NONE,
            CutPolicy.live(),
            null,
            Duration.ZERO);
    service.start(Duration.ofMillis(10));
  }

  private HistoryManifest awaitPart(final UUID history) {
    final var key = ObjectNames.historyPart(LOG, history);
    return await("the part of history " + history)
        .until(
            () -> store.get(key).map(PublisherServiceTest::read).orElse(null),
            part -> part != null);
  }

  private static HistoryManifest read(final byte[] json) {
    try {
      return ManifestJson.read(json);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static HistoryManifest manifest(
      final UUID history, final Instant createdAt, final Base base) {
    return new HistoryManifest(1, "c", "default", history, createdAt, Map.of(1, base));
  }

  private static final class EmptySource implements EntrySource {

    @Override
    public long firstIndex() {
      return 1;
    }

    @Override
    public void seek(final long index) {}

    @Override
    public @Nullable SourcedEntry next() {
      return null;
    }
  }
}
