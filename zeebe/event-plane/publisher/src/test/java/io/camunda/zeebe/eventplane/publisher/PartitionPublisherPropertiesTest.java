/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import static io.camunda.zeebe.eventplane.publisher.PartitionPublisherTest.HISTORY;
import static io.camunda.zeebe.eventplane.publisher.PartitionPublisherTest.LOG;
import static io.camunda.zeebe.eventplane.publisher.PartitionPublisherTest.MANIFEST;
import static io.camunda.zeebe.eventplane.publisher.PartitionPublisherTest.PART_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.publisher.Doubles.Announcements;
import io.camunda.zeebe.eventplane.publisher.Doubles.Clock;
import io.camunda.zeebe.eventplane.publisher.Doubles.FlakyStore;
import io.camunda.zeebe.eventplane.publisher.Doubles.Floors;
import io.camunda.zeebe.eventplane.publisher.Doubles.ListSource;
import io.camunda.zeebe.eventplane.segment.Entry;
import io.camunda.zeebe.eventplane.segment.Footer;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

/**
 * Whatever the commit pace, clock, failing puts and floors, and restarts, a partition's segments
 * hold every entry in order without gaps, overlap only after a restart, and are announced after
 * their floor, whose position is that of the last record at or before its index. The journal is
 * shared by all tries, each appending its own entries; each try has a store of its own.
 */
final class PartitionPublisherPropertiesTest {

  private static Path data;
  private static RaftJournal journal;
  private static int stores;

  @BeforeContainer
  static void openJournal() throws IOException {
    data = Files.createTempDirectory("publisher-properties");
    journal = new RaftJournal(data.resolve("journal"));
  }

  @AfterContainer
  static void closeJournal() throws IOException {
    journal.close();
    try (final var files = Files.walk(data)) {
      for (final var file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }

  @Property(tries = 300)
  void segmentsHoldEveryEntryInOrder(
      @ForAll("journals") final List<Integer> entryRecords,
      @ForAll @IntRange(min = 1, max = 3000) final int maxBytes,
      @ForAll @IntRange(min = 1, max = 10) final int maxAgeSeconds,
      @ForAll("steps") final List<Step> steps)
      throws Exception {
    final var entries = append(entryRecords);
    final var events = new ArrayList<String>();
    final var store = new FlakyStore(data.resolve("store-" + stores++), events);
    final var floors = new Floors(events);
    final var announcements = new Announcements(events);
    final var clock = new Clock();
    final var policy = new CutPolicy(maxBytes, Duration.ofSeconds(maxAgeSeconds));
    int committed = 0;
    boolean restarted = false;

    var source = new ListSource(entries).committed(committed);
    var publisher = start(source, store, floors, announcements, policy, clock);
    for (final var step : steps) {
      committed = Math.min(entries.size(), committed + step.commit());
      clock.advance(Duration.ofSeconds(step.seconds()));
      store.failures = step.failingPuts();
      floors.failures = step.failingFloors();
      if (step.restart()) {
        // A new leader: whatever the old one had begun or had pending is gone.
        restarted = true;
        publisher.close();
        store.failures = 0;
        source = new ListSource(entries).committed(committed);
        publisher = start(source, store, floors, announcements, policy, clock);
      } else {
        source.committed(committed);
        try {
          publisher.poll();
        } catch (final IOException expected) {
          // The segment stays pending for the next call.
        }
      }
    }
    store.failures = 0;
    floors.failures = 0;
    source.committed(entries.size());
    publisher.flush();

    assertThat(events.getFirst()).isEqualTo("put " + PART_KEY);
    final var segments = new TreeMap<Long, List<Entry>>();
    final var prefix = ObjectNames.segmentsPrefix(LOG, HISTORY);
    for (final var object : store.list(prefix, null, 1000)) {
      final var content = store.get(object.key()).orElseThrow();
      final var footer = Footer.read(ByteBuffer.wrap(content));
      final var read = PartitionPublisherTest.read(content);
      assertThat(read).isNotEmpty();
      assertThat(footer.latestRecordTimestamp()).isEqualTo(latest(entries, read));
      segments.merge(footer.firstIndex(), read, (a, b) -> a.size() >= b.size() ? a : b);
    }
    // Every entry, in order, with segments that overlap only after a restart.
    long next = entries.getFirst().entry().index();
    long total = 0;
    for (final var segment : segments.values()) {
      assertThat(segment.getFirst().index()).isLessThanOrEqualTo(next);
      if (!restarted) {
        assertThat(segment.getFirst().index()).isEqualTo(next);
      }
      next = Math.max(next, segment.getLast().index() + 1);
      total += segment.size();
    }
    assertThat(next).isEqualTo(entries.getLast().entry().index() + 1);
    if (!restarted) {
      assertThat(total).isEqualTo(entries.size());
    }
    final var reread = new TreeMap<Long, Entry>();
    segments.values().forEach(s -> s.forEach(e -> reread.put(e.index(), e)));
    assertThat(List.copyOf(reread.values())).isEqualTo(PartitionPublisherTest.entries(entries));
    // The floor is the last entry, and holds the position of the last record at or before it.
    final long lastIndex = entries.getLast().entry().index();
    assertThat(floors.floor)
        .isEqualTo(new UploadFloor(HISTORY, lastIndex, lastPosition(entries, lastIndex)));
    for (final var floor : floors.stored) {
      assertThat(floor.position()).isEqualTo(lastPosition(entries, floor.index()));
    }
    // Each announcement directly follows the floor of its segment.
    for (int i = 0; i < events.size(); i++) {
      if (events.get(i).startsWith("announce ")) {
        assertThat(events.get(i - 1)).isEqualTo("floor " + events.get(i).substring(9));
      }
    }
  }

  /** Entries to append: 0 for an entry without records, else how many records it holds. */
  @Provide
  Arbitrary<List<Integer>> journals() {
    return Arbitraries.frequencyOf(
            Tuple.of(1, Arbitraries.just(0)), Tuple.of(4, Arbitraries.integers().between(1, 5)))
        .list()
        .ofMinSize(1)
        .ofMaxSize(30);
  }

  @Provide
  Arbitrary<List<Step>> steps() {
    final var step =
        Combinators.combine(
                Arbitraries.integers().between(0, 4),
                Arbitraries.integers().between(0, 6),
                Arbitraries.frequencyOf(
                    Tuple.of(4, Arbitraries.just(0)), Tuple.of(1, Arbitraries.just(1))),
                Arbitraries.frequencyOf(
                    Tuple.of(4, Arbitraries.just(0)), Tuple.of(1, Arbitraries.just(1))),
                Arbitraries.frequencyOf(
                    Tuple.of(9, Arbitraries.just(false)), Tuple.of(1, Arbitraries.just(true))))
            .as(Step::new);
    return step.list().ofMaxSize(20);
  }

  private static List<SourcedEntry> append(final List<Integer> entryRecords) {
    final var entries = new ArrayList<SourcedEntry>();
    for (final int records : entryRecords) {
      entries.add(
          records == 0 ? journal.initial() : journal.records(records, 1_000 + entries.size()));
    }
    return entries;
  }

  private static PartitionPublisher start(
      final ListSource source,
      final FlakyStore store,
      final Floors floors,
      final Announcements announcements,
      final CutPolicy policy,
      final Clock clock)
      throws IOException {
    final var publisher =
        new PartitionPublisher(LOG, MANIFEST, source, store, floors, announcements, policy, clock);
    publisher.start();
    return publisher;
  }

  private static long latest(final List<SourcedEntry> entries, final List<Entry> segment) {
    return entries.stream()
        .filter(e -> segment.contains(e.entry()))
        .mapToLong(SourcedEntry::latestRecordTimestamp)
        .max()
        .orElse(Entry.NO_POSITION);
  }

  /** The position of the last record in the entries up to and including {@code index}, or -1. */
  private static long lastPosition(final List<SourcedEntry> entries, final long index) {
    return entries.stream()
        .map(SourcedEntry::entry)
        .filter(e -> e.index() <= index && e.hasRecords())
        .mapToLong(Entry::lastPosition)
        .max()
        .orElse(Entry.NO_POSITION);
  }

  /** What happens before one poll: entries commit, time passes, puts or floors fail, or not. */
  record Step(int commit, int seconds, int failingPuts, int failingFloors, boolean restart) {}
}
