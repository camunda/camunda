/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.history.Histories;
import io.camunda.zeebe.eventplane.history.HistoryManifest;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.history.ManifestJson;
import io.camunda.zeebe.eventplane.publisher.Doubles.Announcements;
import io.camunda.zeebe.eventplane.publisher.Doubles.Clock;
import io.camunda.zeebe.eventplane.publisher.Doubles.FlakyStore;
import io.camunda.zeebe.eventplane.publisher.Doubles.Floors;
import io.camunda.zeebe.eventplane.publisher.Doubles.ListSource;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.EntryReader;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.SegmentName;
import io.camunda.zeebe.eventplane.store.Ulid;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PartitionPublisherTest {

  static final LogId LOG = new LogId("cluster-1", "default", 1);
  static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  static final HistoryManifest MANIFEST =
      new HistoryManifest(
          1,
          "cluster-1",
          "default",
          HISTORY,
          Instant.parse("2026-10-20T10:00:00Z"),
          Map.of(1, Base.NOTHING));
  static final String PART_KEY =
      "cluster-1/default/histories/7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071/1.json";

  /** A segment age that no test reaches by accident. */
  private static final Duration DAY = Duration.ofDays(1);

  @TempDir Path data;
  private RaftJournal journal;
  private final List<String> events = new ArrayList<>();
  private FlakyStore store;
  private final Floors floors = new Floors(events);
  private final Announcements announcements = new Announcements(events);
  private final Clock clock = new Clock();

  @BeforeEach
  void openJournal() {
    journal = new RaftJournal(data.resolve("journal"));
    store = new FlakyStore(data.resolve("store"), events);
  }

  @AfterEach
  void closeJournal() {
    journal.close();
  }

  @Test
  void shouldWriteTheManifestPartBeforeAnySegment() throws Exception {
    // given
    final var entries = List.of(journal.initial(), journal.records(3, 1000));
    final var publisher = publisher(new ListSource(entries), new CutPolicy(1 << 20, DAY));

    // when
    publisher.start();
    final var uploads = publisher.flush();

    // then
    assertThat(store.get(PART_KEY).orElseThrow()).isEqualTo(ManifestJson.write(MANIFEST));
    assertThat(events)
        .containsExactly(
            "put " + PART_KEY, "put " + uploads.getFirst().key(), "floor 2", "announce 2");
    assertThat(read(uploads.getFirst())).isEqualTo(entries(entries));
    assertThat(uploads.getFirst().footer())
        .isEqualTo(
            new Footer(
                HISTORY,
                1,
                2,
                1,
                3,
                1000,
                uploads.getFirst().footer().bodyLength(),
                io.camunda.zeebe.eventplane.segment.Level.PUBLISHER));
  }

  @Test
  void shouldWriteTheManifestPartAtStartAsOneOfTheTenantsHistories() throws Exception {
    // given
    final var publisher = publisher(new ListSource(List.of(journal.initial())), CutPolicy.live());

    // when
    publisher.start();

    // then
    assertThat(store.get(PART_KEY).orElseThrow()).isEqualTo(ManifestJson.write(MANIFEST));
    assertThat(Histories.load(store, "cluster-1", "default").manifest(HISTORY)).hasValue(MANIFEST);
    assertThat(events).containsExactly("put " + PART_KEY);
  }

  @Test
  void shouldWriteTheSameManifestPartWhenStartedAgainByANewPublisher() throws Exception {
    // given
    final var entries = new ListSource(List.of(journal.initial()));
    publisher(entries, CutPolicy.live()).start();
    final var first = store.get(PART_KEY).orElseThrow();

    // when
    publisher(entries, CutPolicy.live()).start();

    // then
    assertThat(store.get(PART_KEY).orElseThrow()).isEqualTo(first);
    assertThat(events).containsExactly("put " + PART_KEY, "put " + PART_KEY);
  }

  @Test
  void shouldCutASegmentOnceItsEntriesReachTheSize() throws Exception {
    // given
    final var entries = new ArrayList<SourcedEntry>();
    for (int i = 0; i < 10; i++) {
      entries.add(journal.records(1, 1000 + i));
    }
    final long entrySize = entries.getFirst().entry().serializedRecord().length;
    final var publisher = publisher(new ListSource(entries), new CutPolicy(3 * entrySize, DAY));

    // when
    publisher.start();
    final var uploads = publisher.poll();

    // then three segments of three entries, and the tenth entry waits for more
    assertThat(uploads).extracting(u -> u.footer().lastIndex()).containsExactly(3L, 6L, 9L);
    assertThat(uploads.getLast().footer().latestRecordTimestamp()).isEqualTo(1008);
    assertThat(publisher.flush()).extracting(u -> u.footer().firstIndex()).containsExactly(10L);
  }

  @Test
  void shouldCutASegmentOnceItIsOldEnough() throws Exception {
    // given
    final var source =
        new ListSource(List.of(journal.records(1, 1000), journal.records(1, 1001))).committed(1);
    final var publisher = publisher(source, new CutPolicy(1 << 20, Duration.ofSeconds(5)));
    publisher.start();
    assertThat(publisher.poll()).isEmpty();
    clock.advance(Duration.ofSeconds(4));
    source.committed(2);
    assertThat(publisher.poll()).isEmpty();

    // when
    clock.advance(Duration.ofSeconds(1));

    // then
    assertThat(publisher.poll()).extracting(u -> u.footer().lastIndex()).containsExactly(2L);
  }

  @Test
  void shouldHaveNoPositionsInASegmentWithoutRecords() throws Exception {
    // given
    final var publisher = publisher(new ListSource(List.of(journal.initial())), CutPolicy.live());

    // when
    publisher.start();
    final var footer = publisher.flush().getFirst().footer();

    // then
    assertThat(footer.hasRecords()).isFalse();
    assertThat(footer.latestRecordTimestamp()).isEqualTo(Entry.NO_POSITION);
  }

  @Test
  void shouldNameASegmentByItsLogHistoryIndexesAndLevel() throws Exception {
    // given
    final var entries = List.of(journal.initial(), journal.records(3, 1000));
    final var publisher = publisher(new ListSource(entries), CutPolicy.live());

    // when
    publisher.start();
    final var upload = publisher.flush().getFirst();

    // then
    final var name = ObjectNames.parseSegment(upload.key());
    assertThat(Ulid.isValid(name.ulid())).isTrue();
    assertThat(name).isEqualTo(new SegmentName(HISTORY, 1, 2, 0, name.ulid()));
    assertThat(upload.key())
        .isEqualTo(
            "cluster-1/default/1/%s/00000000000000000001-00000000000000000002-L0-%s.seg"
                .formatted(HISTORY, name.ulid()));
    assertThat(store.head(upload.key()))
        .hasValueSatisfying(object -> assertThat(object.size()).isEqualTo(upload.size()));
  }

  @Test
  void shouldEncodeTheClusterAndTenantInTheKeyOfASegment() throws Exception {
    // given
    final var log = new LogId("my cluster", "a/b", 2);
    final var manifest =
        new HistoryManifest(
            1,
            "my cluster",
            "a/b",
            HISTORY,
            Instant.parse("2026-10-20T10:00:00Z"),
            Map.of(2, Base.NOTHING));
    final var publisher =
        new PartitionPublisher(
            log,
            manifest,
            new ListSource(List.of(journal.initial())),
            store,
            floors,
            announcements,
            CutPolicy.live(),
            clock);

    // when
    publisher.start();
    final var upload = publisher.flush().getFirst();

    // then
    assertThat(upload.key()).startsWith("my%20cluster/a%2Fb/2/" + HISTORY + "/");
    assertThat(events.getFirst())
        .isEqualTo("put my%20cluster/a%2Fb/histories/" + HISTORY + "/2.json");
  }

  @Test
  void shouldTryAFailedPutAgainWithTheSameKeyAndBytes() throws Exception {
    // given
    final var entries = List.of(journal.records(2, 1000), journal.records(1, 1001));
    final var source = new ListSource(entries).committed(1);
    final var publisher = publisher(source, new CutPolicy(1, DAY));
    publisher.start();
    store.failures = 1;

    // when
    assertThatThrownBy(publisher::poll).isInstanceOf(IOException.class);
    source.committed(2);
    final var uploads = publisher.poll();

    // then
    final var attempts = store.puts.subList(1, 3);
    assertThat(attempts).extracting(Doubles.Put::failed).containsExactly(true, false);
    assertThat(attempts.get(1).key()).isEqualTo(attempts.get(0).key());
    assertThat(attempts.get(1).content()).isEqualTo(attempts.get(0).content());
    assertThat(uploads).extracting(u -> u.footer().lastIndex()).containsExactly(1L, 2L);
    assertThat(uploads.getFirst().key()).isEqualTo(attempts.get(0).key());
    assertThat(announcements.uploads).isEqualTo(uploads);
  }

  @Test
  void shouldAnnounceOnlyAfterTheFloorIsStored() throws Exception {
    // given
    final var publisher =
        publisher(new ListSource(List.of(journal.records(1, 1000))), CutPolicy.live());
    publisher.start();
    floors.failures = 1;

    // when
    assertThatThrownBy(publisher::flush).isInstanceOf(IOException.class);
    assertThat(announcements.uploads).isEmpty();
    final var uploads = publisher.poll();

    // then
    final var key = uploads.getFirst().key();
    assertThat(events.subList(1, events.size()))
        .containsExactly("put " + key, "failed floor 1", "put " + key, "floor 1", "announce 1");
  }

  @Test
  void shouldKeepThePositionOfTheLastRecordUploadedAcrossSegmentsWithoutRecords() throws Exception {
    // given
    final var entries =
        List.of(
            journal.initial(),
            journal.records(3, 1000),
            journal.configuration(),
            journal.records(2, 1001));
    final var publisher = publisher(new ListSource(entries), new CutPolicy(1, DAY));

    // when
    publisher.start();
    publisher.flush();

    // then a segment of one entry each, and only the ones with records move the position
    assertThat(floors.stored)
        .containsExactly(
            new UploadFloor(HISTORY, 1, -1),
            new UploadFloor(HISTORY, 2, 3),
            new UploadFloor(HISTORY, 3, 3),
            new UploadFloor(HISTORY, 4, 5));
  }

  @Test
  void shouldTakeTheFloorsPositionToASegmentWithoutRecordsAfterARestart() throws Exception {
    // given
    final var entries = List.of(journal.records(3, 1000), journal.configuration());
    floors.floor = new UploadFloor(HISTORY, 1, 3);
    final var publisher = publisher(new ListSource(entries), CutPolicy.live());

    // when
    publisher.start();
    final var upload = publisher.flush().getFirst();

    // then
    assertThat(upload.footer().hasRecords()).isFalse();
    assertThat(floors.stored).containsExactly(new UploadFloor(HISTORY, 2, 3));
  }

  @Test
  void shouldStartAtTheFirstEntryWithoutAFloor() throws Exception {
    // given
    final var entries = List.of(journal.records(1, 1000), journal.records(1, 1001));
    final var source = new ListSource(entries);
    final var publisher = publisher(source, CutPolicy.live());

    // when
    publisher.start();

    // then
    assertThat(source.seeks).containsExactly(1L);
    assertThat(publisher.flush().getFirst().footer().firstIndex()).isEqualTo(1);
  }

  @Test
  void shouldResumeAfterTheFloor() throws Exception {
    // given
    final var entries = List.of(journal.records(1, 1000), journal.records(1, 1001));
    final var source = new ListSource(entries);
    floors.floor = new UploadFloor(HISTORY, 1, 1);
    final var publisher = publisher(source, CutPolicy.live());

    // when
    publisher.start();

    // then
    assertThat(source.seeks).containsExactly(2L);
    assertThat(publisher.flush().getFirst().footer().firstIndex()).isEqualTo(2);
  }

  @Test
  void shouldStartAtTheFirstEntryAndWarnWhenTheJournalWasCompactedPastTheFloor() throws Exception {
    // given a journal that holds entries 3 and 4, with the floor at entry 1
    final var all =
        List.of(
            journal.initial(),
            journal.configuration(),
            journal.records(2, 1000),
            journal.records(1, 1001));
    final var source = new ListSource(all.subList(2, 4));
    floors.floor = new UploadFloor(HISTORY, 1, -1);
    final var publisher = publisher(source, CutPolicy.live());

    try (final var logs = LogCapturer.capturing(PartitionPublisher.class, Level.WARN)) {
      // when
      publisher.start();

      // then
      assertThat(logs.messagesAt(Level.WARN))
          .containsExactly(
              ("Expected the journal of cluster-1/default/1 to hold entry 2, which follows the upload floor at index 1,"
                  + " but its first entry is 3: it was compacted past the floor, so entries 2"
                  + " to 2 are not published"));
    }
    assertThat(source.seeks).containsExactly(3L);
    final var footer = publisher.flush().getFirst().footer();
    assertThat(footer.firstIndex()).isEqualTo(3);
    assertThat(footer.lastIndex()).isEqualTo(4);
  }

  @Test
  void shouldNotWarnWhenTheJournalStartsRightAfterTheFloor() throws Exception {
    // given a journal that holds entries 3 and 4, with the floor at entry 2
    final var all =
        List.of(
            journal.initial(),
            journal.configuration(),
            journal.records(2, 1000),
            journal.records(1, 1001));
    final var source = new ListSource(all.subList(2, 4));
    floors.floor = new UploadFloor(HISTORY, 2, -1);
    final var publisher = publisher(source, CutPolicy.live());

    try (final var logs = LogCapturer.capturing(PartitionPublisher.class, Level.WARN)) {
      // when
      publisher.start();

      // then
      assertThat(logs.messages()).isEmpty();
    }
    assertThat(source.seeks).containsExactly(3L);
  }

  @Test
  void shouldRejectAManifestOfAnotherCluster() {
    // given
    final var other = new LogId("cluster-2", "default", 1);

    // when
    assertThatThrownBy(() -> publisher(other, MANIFEST))
        // then
        .isInstanceOfSatisfying(
            ManifestClusterMismatchException.class,
            e -> {
              assertThat(e.log()).isEqualTo(other);
              assertThat(e.manifest()).isEqualTo(MANIFEST);
            })
        .hasMessage("Expected a manifest of cluster 'cluster-2', but got one of 'cluster-1'");
  }

  @Test
  void shouldRejectAManifestOfAnotherTenant() {
    // given
    final var other = new LogId("cluster-1", "other", 1);

    // when
    assertThatThrownBy(() -> publisher(other, MANIFEST))
        // then
        .isInstanceOf(ManifestTenantMismatchException.class)
        .hasMessage("Expected a manifest of tenant 'other', but got one of 'default'");
  }

  @Test
  void shouldRejectAManifestOfAnotherPartition() {
    // given
    final var other = new LogId("cluster-1", "default", 2);

    // when
    assertThatThrownBy(() -> publisher(other, MANIFEST))
        // then
        .isInstanceOf(ManifestPartitionsMismatchException.class)
        .hasMessage(
            "Expected history %s to hold only partition 2, but it holds [1]".formatted(HISTORY));
  }

  @Test
  void shouldRejectAManifestOfMorePartitionsThanTheLogs() {
    // given a whole history's manifest rather than the log's part of it
    final var whole =
        new HistoryManifest(
            1,
            "cluster-1",
            "default",
            HISTORY,
            Instant.parse("2026-10-20T10:00:00Z"),
            Map.of(1, Base.NOTHING, 2, Base.NOTHING));

    // when
    assertThatThrownBy(() -> publisher(LOG, whole))
        // then
        .isInstanceOf(ManifestPartitionsMismatchException.class)
        .hasMessage(
            "Expected history %s to hold only partition 1, but it holds [1, 2]".formatted(HISTORY));
  }

  @Test
  void shouldRejectPollingBeforeStart() throws Exception {
    // given
    final var publisher = publisher(new ListSource(List.of()), CutPolicy.live());

    // when
    assertThatThrownBy(publisher::poll)
        // then
        .isInstanceOf(PublisherNotStartedException.class)
        .hasMessage("Expected the publisher of %s to be started, but it isn't".formatted(LOG));
    assertThatThrownBy(publisher::flush).isInstanceOf(PublisherNotStartedException.class);
  }

  @Test
  void shouldRejectStartingTwice() throws Exception {
    // given
    final var publisher = publisher(new ListSource(List.of()), CutPolicy.live());
    publisher.start();

    // when
    assertThatThrownBy(publisher::start)
        // then
        .isInstanceOfSatisfying(
            PublisherAlreadyStartedException.class, e -> assertThat(e.log()).isEqualTo(LOG))
        .hasMessage("Expected the publisher of %s not to be started, but it is".formatted(LOG));
  }

  private PartitionPublisher publisher(final EntrySource source, final CutPolicy policy) {
    return new PartitionPublisher(
        LOG, MANIFEST, source, store, floors, announcements, policy, clock);
  }

  private PartitionPublisher publisher(final LogId log, final HistoryManifest manifest) {
    return new PartitionPublisher(
        log,
        manifest,
        new ListSource(List.of()),
        store,
        floors,
        announcements,
        CutPolicy.live(),
        clock);
  }

  /** A segment's entries, read from its object as a consumer reads them. */
  private List<Entry> read(final Upload upload) throws IOException {
    return read(store.get(upload.key()).orElseThrow());
  }

  static List<Entry> read(final byte[] segment) throws IOException {
    final var footer = Footer.read(ByteBuffer.wrap(segment));
    final var entries = new ArrayList<Entry>();
    final var body = Arrays.copyOf(segment, (int) footer.bodyLength());
    try (final var reader = new EntryReader(new ByteArrayInputStream(body))) {
      for (var entry = reader.next(); entry != null; entry = reader.next()) {
        entries.add(entry);
      }
    }
    return entries;
  }

  static List<Entry> entries(final List<SourcedEntry> entries) {
    return entries.stream().map(SourcedEntry::entry).toList();
  }
}
