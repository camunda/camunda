/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.eventplane.history.HistoryIds;
import io.camunda.zeebe.eventplane.history.HistoryManifest.Base;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.Level;
import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.segment.SmallFrames;
import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.Ulid;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogReaderTest {

  private static final LogId LOG = new LogId("c", "default", 1);
  private static final UUID HISTORY = HistoryIds.initial("c", "default");
  private static final Instant CREATED = Instant.parse("2026-10-20T12:00:00Z");

  @TempDir Path directory;
  private RecordingStore store;
  private TestLog log;
  private TestJournal journal;
  private final List<Entry> entries = new ArrayList<>();
  private final List<Record<?>> records = new ArrayList<>();
  private final RecordHandler handler = (log, record) -> records.add(record);
  private final Listener listener = new Listener();
  private final AtomicLong clock = new AtomicLong();

  @BeforeEach
  void setUp() throws IOException {
    store = new RecordingStore(new FileSystemStore(directory.resolve("store")));
    log = new TestLog(store, LOG);
    journal = new TestJournal(directory.resolve("journal"));
    log.history(HISTORY, Base.NOTHING, CREATED);
    // Index 1 holds no records; index i after it holds positions 2i - 3 and 2i - 2.
    entries.add(journal.initial());
    entries.addAll(journal.jobEntries(23, 2));
  }

  @AfterEach
  void tearDown() {
    journal.close();
  }

  @Test
  void shouldReadRecordsAcrossManySegmentsInPositionOrder() throws IOException {
    // given
    for (int first = 1; first <= 24; first += 3) {
      log.upload(HISTORY, entries(first, first + 2));
    }
    final var reader = reader(Start.EARLIEST);

    // when
    final boolean progress = reader.poll(handler);

    // then
    assertThat(progress).isTrue();
    assertThat(positions()).containsExactlyElementsOf(range(1, 46));
    assertThat(records)
        .extracting(record -> ((JobRecordValue) record.getValue()).getType())
        .startsWith("job-1", "job-2", "job-3")
        .endsWith("job-46");
    assertThat(reader.cursor()).contains(new Cursor(HISTORY, 24, 46));
    assertThat(reader.poll(handler)).isFalse();
    assertThat(listener.gaps).isEmpty();
  }

  @Test
  void shouldListOnlyAfterTheNextIndexAtTheHead() throws IOException {
    // given a reader that read everything there is
    for (int first = 1; first <= 24; first += 3) {
      log.upload(HISTORY, entries(first, first + 2));
    }
    final var reader = reader(Start.EARLIEST);
    reader.poll(handler);
    final var directory = ObjectNames.segmentsPrefix(new LogId("c", "default", 1), HISTORY);
    final long before = wholeListings(directory);

    // when it polls at the head
    for (int poll = 0; poll < 10; poll++) {
      assertThat(reader.poll(handler)).isFalse();
    }

    // then, having listed the whole directory once when it reached the head, it lists only past
    // the next index
    assertThat(wholeListings(directory) - before).isZero();
  }

  @Test
  void shouldLookForHistoriesAndMarkersOnlyEveryIntervalAtTheHead() throws IOException {
    // given a reader that read everything there is
    log.upload(HISTORY, entries(1, 24));
    final var reader =
        new LogReader(
            store,
            LOG,
            Start.EARLIEST,
            listener,
            Duration.ofSeconds(10),
            Duration.ofMinutes(1),
            clock::get);
    reader.poll(handler);
    final var histories = ObjectNames.historiesPrefix("c", "default");
    final var markers = ObjectNames.retentionPrefix(new LogId("c", "default", 1));
    final long historiesBefore = listings(histories);
    final long markersBefore = listings(markers);

    // when it polls at the head within the interval
    for (int poll = 0; poll < 10; poll++) {
      clock.addAndGet(Duration.ofSeconds(1).toNanos());
      reader.poll(handler);
    }

    // then it doesn't look again, until the interval is over
    assertThat(listings(histories)).isEqualTo(historiesBefore);
    assertThat(listings(markers)).isEqualTo(markersBefore);
    clock.addAndGet(Duration.ofMinutes(1).toNanos());
    reader.poll(handler);
    assertThat(listings(histories)).isEqualTo(historiesBefore + 1);
    assertThat(listings(markers)).isEqualTo(markersBefore + 1);
  }

  private long wholeListings(final String directory) {
    return store.listings().stream()
        .filter(listing -> listing.prefix().equals(directory) && listing.startAfter() == null)
        .count();
  }

  private long listings(final String prefix) {
    return store.listings().stream().filter(listing -> listing.prefix().equals(prefix)).count();
  }

  @Test
  void shouldGoOnAfterACursor() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 12));
    log.upload(HISTORY, entries(13, 24));

    // when
    reader(new Cursor(HISTORY, 10, 18)).poll(handler);

    // then
    assertThat(positions()).containsExactlyElementsOf(range(19, 46));
  }

  @Test
  void shouldGoOnInsideAnEntryAfterACursor() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 24));

    // when the cursor has the first of entry 11's two records
    reader(new Cursor(HISTORY, 10, 19)).poll(handler);

    // then
    assertThat(positions()).containsExactlyElementsOf(range(20, 46));
  }

  @Test
  void shouldReadOnInARollupThatReplacedSegmentsWhileReading() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    final var replaced =
        List.of(log.upload(HISTORY, entries(5, 8)), log.upload(HISTORY, entries(9, 12)));
    final var rolledUp = new AtomicBoolean();
    final RecordHandler rollingUp =
        (log, record) -> {
          records.add(record);
          if (record.getPosition() == 6 && !rolledUp.getAndSet(true)) {
            rollUp(entries(1, 12), replaced);
          }
        };

    // when
    reader(Start.EARLIEST).poll(rollingUp);

    // then
    assertThat(positions()).containsExactlyElementsOf(range(1, 22));
  }

  @Test
  void shouldListAgainWhenASegmentIsGoneBeforeItIsRead() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    final var replaced = log.upload(HISTORY, entries(5, 8));
    store.beforeRange(
        key -> {
          if (key.equals(replaced)) {
            store.beforeRange(ignored -> {});
            rollUp(entries(1, 8), List.of(replaced));
          }
        });

    // when
    reader(Start.EARLIEST).poll(handler);

    // then
    assertThat(positions()).containsExactlyElementsOf(range(1, 14));
  }

  @Test
  void shouldDeliverEveryRecordOfOverlappingSegmentsOfTwoLeaders() throws IOException {
    // given the new leader resumed at index 1, while the old one had uploaded more
    log.upload(HISTORY, entries(1, 4));
    log.upload(HISTORY, entries(5, 8));
    log.upload(HISTORY, entries(9, 12));
    log.upload(HISTORY, entries(1, 6));

    // when
    reader(Start.EARLIEST).poll(handler);

    // then
    assertThat(firstArrivals()).containsExactlyElementsOf(range(1, 22));
  }

  @Test
  void shouldReadFromTheFrameOfTheCursor() throws IOException {
    // given a rollup whose frames hold an entry or two each
    final var out = new ByteArrayOutputStream();
    try (var writer = SmallFrames.writer(out, LOG, HISTORY, Level.ROLLUP, 200)) {
      for (final var entry : entries(1, 24)) {
        writer.append(entry);
      }
      writer.finish(1_000);
    }
    final var key =
        ObjectNames.segment(new LogId("c", "default", 1), HISTORY, 1, 24, 1, Ulid.next());
    store.put(key, out.toByteArray());

    // when
    reader(new Cursor(HISTORY, 20, 38)).poll(handler);

    // then
    assertThat(positions()).containsExactlyElementsOf(range(39, 46));
    // the first read is the tail, then come the frames from the cursor's on
    final var reads = store.ranges().stream().filter(range -> range.key().equals(key)).toList();
    assertThat(reads).hasSizeGreaterThan(1);
    assertThat(reads.get(1).offset()).isPositive();
  }

  @Test
  void shouldReportAGapUpToARetentionMarker() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    final var removed = log.upload(HISTORY, entries(5, 8));
    log.upload(HISTORY, entries(9, 12));
    log.retention(HISTORY, 9, 15);
    log.delete(removed);
    final var reader = reader(new Cursor(HISTORY, 4, 6));

    // when
    reader.poll(handler);

    // then
    assertThat(listener.gaps).containsExactly(List.of(5L, 8L));
    assertThat(positions()).containsExactlyElementsOf(range(15, 22));
    assertThat(reader.cursor()).contains(new Cursor(HISTORY, 12, 22));
  }

  @Test
  void shouldFindNothingNewWhileNoSegmentHoldsTheNextEntry() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    final var reader = reader(Start.EARLIEST);
    reader.poll(handler);

    // when
    final boolean idle = reader.poll(handler);
    log.upload(HISTORY, entries(5, 8));
    final boolean progress = reader.poll(handler);

    // then
    assertThat(idle).isFalse();
    assertThat(progress).isTrue();
    assertThat(positions()).containsExactlyElementsOf(range(1, 14));
    assertThat(listener.gaps).isEmpty();
  }

  @Test
  void shouldStartAtTheFirstSegmentThereIs() throws IOException {
    // given the log's first segment starts late, as when the journal was compacted before
    log.upload(HISTORY, entries(7, 12));
    log.upload(HISTORY, entries(13, 24));

    // when
    reader(Start.EARLIEST).poll(handler);

    // then
    assertThat(positions()).containsExactlyElementsOf(range(11, 46));
    assertThat(listener.gaps).isEmpty();
  }

  @Test
  void shouldReportAHoleOnceItLastedTheGracePeriod() throws IOException {
    // given entries 5 to 8 never arrived, and no retention marker explains it
    log.upload(HISTORY, entries(1, 4));
    log.upload(HISTORY, entries(9, 12));
    final var reader = reader(Start.EARLIEST);
    reader.poll(handler);

    // when
    clock.addAndGet(Duration.ofSeconds(9).toNanos());
    final boolean beforeGrace = reader.poll(handler);
    clock.addAndGet(Duration.ofSeconds(1).toNanos());
    final boolean afterGrace = reader.poll(handler);

    // then
    assertThat(beforeGrace).isFalse();
    assertThat(afterGrace).isTrue();
    assertThat(listener.gaps).containsExactly(List.of(5L, 8L));
    assertThat(positions()).containsExactlyElementsOf(concat(range(1, 6), range(15, 22)));
  }

  @Test
  void shouldReadASegmentThatArrivesLateWithinTheGracePeriod() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    log.upload(HISTORY, entries(9, 12));
    final var reader = reader(Start.EARLIEST);
    reader.poll(handler);
    clock.addAndGet(Duration.ofSeconds(9).toNanos());
    reader.poll(handler);

    // when
    log.upload(HISTORY, entries(5, 8));
    clock.addAndGet(Duration.ofSeconds(9).toNanos());
    reader.poll(handler);

    // then
    assertThat(listener.gaps).isEmpty();
    assertThat(positions()).containsExactlyElementsOf(range(1, 22));
  }

  @Test
  void shouldStartLatestAfterTheNewestSegment() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    log.upload(HISTORY, entries(5, 8));
    final var reader = reader(Start.LATEST);

    // when
    final boolean progress = reader.poll(handler);
    log.upload(HISTORY, entries(9, 12));
    reader.poll(handler);

    // then
    assertThat(progress).isFalse();
    assertThat(positions()).containsExactlyElementsOf(range(15, 22));
  }

  @Test
  void shouldGoOnInTheNewHistoryAfterARestoreToAnEarlierCheckpoint() throws IOException {
    // given a reader that read to index 10 of the first history
    log.upload(HISTORY, entries(1, 10));
    final var reader = reader(Start.EARLIEST);
    reader.poll(handler);
    // and a restore to index 6, after which the new history appended its own entries
    final var restored = UUID.randomUUID();
    final var checkpoint = new Base(HISTORY, 6, 10);
    log.history(restored, checkpoint, CREATED.plusSeconds(60));
    try (var restoredJournal = new TestJournal(directory.resolve("restored"), "restored-")) {
      entries(1, 6).forEach(restoredJournal::copy);
      log.upload(restored, restoredJournal.jobEntries(3, 2));
    }
    records.clear();

    // when
    reader.poll(handler);

    // then
    assertThat(listener.changes)
        .containsExactly(new Change(new Cursor(HISTORY, 10, 18), checkpoint, restored));
    assertThat(records)
        .extracting(record -> ((JobRecordValue) record.getValue()).getType())
        .containsExactly(
            "restored-11",
            "restored-12",
            "restored-13",
            "restored-14",
            "restored-15",
            "restored-16");
    assertThat(reader.cursor()).contains(new Cursor(restored, 9, 16));
  }

  @Test
  void shouldReadEntriesThatANewHistoryTookOverFromItsSegments() throws IOException {
    // given the first history's segments end at index 3, and the new history uploaded entries it
    // took over from index 4 on
    log.upload(HISTORY, entries(1, 3));
    final var restored = UUID.randomUUID();
    log.history(restored, new Base(HISTORY, 6, 10), CREATED.plusSeconds(60));
    try (var restoredJournal = new TestJournal(directory.resolve("restored"), "restored-")) {
      final var copied = entries(1, 6).stream().map(restoredJournal::copy).toList();
      final var segment = new ArrayList<>(copied.subList(3, 6));
      segment.addAll(restoredJournal.jobEntries(3, 2));
      log.upload(restored, segment);
    }

    // when
    reader(Start.EARLIEST).poll(handler);

    // then
    assertThat(listener.changes).isEmpty();
    assertThat(records)
        .extracting(record -> ((JobRecordValue) record.getValue()).getType())
        .containsExactly(
            "job-1",
            "job-2",
            "job-3",
            "job-4",
            "job-5",
            "job-6",
            "job-7",
            "job-8",
            "job-9",
            "job-10",
            "restored-11",
            "restored-12",
            "restored-13",
            "restored-14",
            "restored-15",
            "restored-16");
  }

  @Test
  void shouldNotReadAnOldHistoryPastWhereTheNewOneTookOver() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 10));
    final var restored = UUID.randomUUID();
    log.history(restored, new Base(HISTORY, 6, 10), CREATED.plusSeconds(60));
    try (var restoredJournal = new TestJournal(directory.resolve("restored"), "restored-")) {
      entries(1, 6).forEach(restoredJournal::copy);
      log.upload(restored, restoredJournal.jobEntries(3, 2));
    }

    // when
    reader(Start.EARLIEST).poll(handler);

    // then
    assertThat(listener.changes).isEmpty();
    assertThat(positions()).containsExactlyElementsOf(range(1, 16));
    assertThat(records.get(10).getValue())
        .isInstanceOfSatisfying(
            JobRecordValue.class, value -> assertThat(value.getType()).isEqualTo("restored-11"));
  }

  @Test
  void shouldStopAtAnEntryBoundary() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 24));
    final var reader = reader(Start.EARLIEST);
    final RecordHandler stopping =
        (log, record) -> {
          records.add(record);
          reader.stop();
        };

    // when
    reader.poll(stopping);

    // then the entry with the record is delivered whole
    assertThat(positions()).containsExactly(1L, 2L);
    assertThat(reader.cursor()).contains(new Cursor(HISTORY, 2, 2));
    assertThat(reader.poll(handler)).isFalse();
  }

  @Test
  void shouldDeliverARecordAgainThatTheHandlerFailedOn() throws IOException {
    // given
    log.upload(HISTORY, entries(1, 4));
    final var reader = reader(Start.EARLIEST);
    final var failed = new AtomicBoolean();
    final RecordHandler failing =
        (log, record) -> {
          if (record.getPosition() == 4 && !failed.getAndSet(true)) {
            throw new IllegalStateException("Expected to fail once");
          }
          records.add(record);
        };

    // when
    assertThatThrownBy(() -> reader.poll(failing)).hasMessage("Expected to fail once");
    final var cursor = reader.cursor();
    reader.poll(failing);

    // then
    assertThat(cursor).contains(new Cursor(HISTORY, 2, 3));
    assertThat(positions()).containsExactlyElementsOf(range(1, 6));
  }

  private LogReader reader(final StartFrom from) {
    return new LogReader(
        store, LOG, from, listener, Duration.ofSeconds(10), Duration.ZERO, clock::get);
  }

  /** The journal's entries from index {@code first} to {@code last}. */
  private List<Entry> entries(final int first, final int last) {
    return List.copyOf(entries.subList(first - 1, last));
  }

  /** Writes a rollup of entries, and deletes the segments it replaces. */
  private void rollUp(final List<Entry> rolledUp, final List<String> replaced) {
    try {
      log.upload(HISTORY, Level.ROLLUP, rolledUp);
      for (final var key : replaced) {
        log.delete(key);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<Long> positions() {
    return records.stream().map(Record::getPosition).toList();
  }

  private List<Long> firstArrivals() {
    final var seen = new HashSet<Long>();
    return positions().stream().filter(seen::add).toList();
  }

  private static List<Long> range(final long from, final long to) {
    return LongStream.rangeClosed(from, to).boxed().toList();
  }

  private static List<Long> concat(final List<Long> a, final List<Long> b) {
    final var all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }

  private record Change(Cursor cursor, Base shared, UUID current) {}

  private static final class Listener implements ConsumerListener {

    private final List<List<Long>> gaps = new ArrayList<>();
    private final List<Change> changes = new ArrayList<>();

    @Override
    public void onGap(final LogId log, final long fromIndex, final long toIndex) {
      gaps.add(List.of(fromIndex, toIndex));
    }

    @Override
    public void onHistoryChange(
        final LogId log, final Cursor cursor, final Base shared, final UUID currentHistory) {
      changes.add(new Change(cursor, shared, currentHistory));
    }
  }
}
