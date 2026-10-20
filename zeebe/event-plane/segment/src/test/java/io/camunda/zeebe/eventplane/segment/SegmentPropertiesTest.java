/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.segment;

import static io.camunda.zeebe.eventplane.segment.Entry.NO_POSITION;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.statistics.Statistics;

/**
 * Properties of segments written from arbitrary journals: what's written reads back, a reader
 * resumes after any position without missing a record, and damaged bytes fail only as {@link
 * InvalidSegmentException}. The journal is shared by all tries, each appending its own entries.
 */
final class SegmentPropertiesTest {

  private static final Identity IDENTITY = new Identity("cluster-1", "default", 3);
  private static final UUID HISTORY = UUID.fromString("7f3a9c0e-4b2d-4e8f-9a1b-2c3d4e5f6071");
  private static final long NOON = 1_792_152_000_000L;

  private static Path directory;
  private static RaftJournal journal;

  @BeforeContainer
  static void openJournal() throws IOException {
    directory = Files.createTempDirectory("segment-properties");
    journal = new RaftJournal(directory);
  }

  @AfterContainer
  static void closeJournal() throws IOException {
    journal.close();
    try (var files = Files.walk(directory)) {
      for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(file);
      }
    }
  }

  @Property(tries = 300)
  void whatIsWrittenReadsBack(
      @ForAll("journals") List<Spec> specs, @ForAll("frameTargets") long frameTarget)
      throws Exception {
    final var entries = append(specs);

    final var segment = write(entries, frameTarget);

    final var read = Read.of(segment);
    final var withRecords = entries.stream().filter(Entry::hasRecords).toList();
    assertThat(read.footer())
        .isEqualTo(
            new Footer(
                HISTORY,
                entries.getFirst().index(),
                entries.getLast().index(),
                withRecords.isEmpty() ? NO_POSITION : withRecords.getFirst().firstPosition(),
                withRecords.isEmpty() ? NO_POSITION : withRecords.getLast().lastPosition(),
                withRecords.isEmpty() ? NO_POSITION : NOON,
                read.footer().bodyLength(),
                level(frameTarget)));
    assertThat(read.metadata().identity()).isEqualTo(IDENTITY);
    assertThat(read.entriesFrom(0)).isEqualTo(entries);

    final var frames = read.metadata().frames();
    if (level(frameTarget) == Level.PUBLISHER) {
      assertThat(frames).hasSize(1);
    }
    final var starts = new ArrayList<Integer>();
    for (var frame : frames) {
      final var fromFrame = read.entriesFrom(frame.offset());
      starts.add(entries.size() - fromFrame.size());
      assertThat(fromFrame).isEqualTo(entries.subList(starts.getLast(), entries.size()));
    }
    starts.add(entries.size());
    for (int i = 0; i < frames.size(); i++) {
      final var inFrame = entries.subList(starts.get(i), starts.get(i + 1));
      assertThat(inFrame).isNotEmpty();
      // A frame starts at its first record, and ends after the entry that brings it to the target.
      assertThat(frames.get(i).firstPosition())
          .isEqualTo(
              inFrame.stream()
                  .filter(Entry::hasRecords)
                  .findFirst()
                  .map(Entry::firstPosition)
                  .orElse(NO_POSITION));
      if (i < frames.size() - 1) {
        assertThat(bytes(inFrame)).isGreaterThanOrEqualTo(frameTarget);
        assertThat(bytes(inFrame.subList(0, inFrame.size() - 1))).isLessThan(frameTarget);
      }
    }
  }

  @Property(tries = 300)
  void aReaderResumesAfterAnyPositionWithoutMissingARecord(
      @ForAll("journals") List<Spec> specs,
      @ForAll("frameTargets") long frameTarget,
      @ForAll @DoubleRange(min = 0, max = 1) double where)
      throws Exception {
    final var entries = append(specs);
    final var segment = write(entries, frameTarget);
    final var read = Read.of(segment);
    // From just before the first record to just after the last.
    final long lowest = Math.max(NO_POSITION, firstPosition(entries) - 2);
    final long highest = Math.max(lowest, lastPosition(entries) + 1);
    final long position = lowest + Math.round(where * (highest - lowest));

    final long start = read.metadata().startAfter(position);

    final var resumed = read.entriesFrom(start);
    final var next =
        entries.stream().filter(e -> e.hasRecords() && e.lastPosition() > position).findFirst();
    if (next.isPresent()) {
      assertThat(resumed).contains(next.get());
      assertThat(resumed.subList(0, resumed.indexOf(next.get())))
          .allMatch(e -> !e.hasRecords() || e.lastPosition() <= position);
    }
    // It starts at the last frame it can: the next frame with records starts after position + 1.
    read.metadata().frames().stream()
        .filter(f -> f.offset() > start && f.firstPosition() != NO_POSITION)
        .findFirst()
        .ifPresent(f -> assertThat(f.firstPosition()).isGreaterThan(position + 1));
  }

  @Property(tries = 1000)
  void damagedBytesFailOnlyAsAnInvalidSegment(
      @ForAll("journals") List<Spec> specs,
      @ForAll("frameTargets") long frameTarget,
      @ForAll Damage damage,
      @ForAll boolean inTail,
      @ForAll @DoubleRange(min = 0, max = 1, maxIncluded = false) double where,
      @ForAll("numbers") long value,
      @ForAll @IntRange(min = -1, max = 700) int position)
      throws Exception {
    final var segment = write(append(specs), frameTarget);
    // Half of the damage hits the footer and the metadata, which are a small part of the bytes.
    final int at =
        inTail
            ? segment.length - 1 - (int) (where * Math.min(segment.length, 256))
            : (int) (where * segment.length);
    final var damaged = damage.apply(segment, at, value);

    // Anything but an InvalidSegmentException, such as an IOException from zstd or an
    // IndexOutOfBoundsException, fails the property.
    String outcome = "read";
    try {
      final var read = Read.of(damaged);
      read.entriesFrom(0);
      read.entriesFrom(read.metadata().startAfter(position));
    } catch (InvalidSegmentException expected) {
      // What a reader gets for bytes that aren't a segment.
      outcome = expected.getClass().getSimpleName();
    }
    // The report shows how often damage is caught, and by which check.
    Statistics.label(damage.name() + (inTail ? " in the tail" : "")).collect(outcome);
  }

  @Provide
  Arbitrary<List<Spec>> journals() {
    final Arbitrary<Spec> records =
        Combinators.combine(
                Arbitraries.integers().between(1, 20), Arbitraries.integers().between(0, 2000))
            .as(Spec.Records::new);
    final Arbitrary<Spec> spec =
        Arbitraries.frequencyOf(
            Tuple.of(1, Arbitraries.just(new Spec.Initial())),
            Tuple.of(1, Arbitraries.just(new Spec.Configuration())),
            Tuple.of(6, records));
    return spec.list().ofMinSize(1).ofMaxSize(30);
  }

  /** Damage values, half of them at the ends of the ranges that lengths and offsets overflow at. */
  @Provide
  Arbitrary<Long> numbers() {
    return Arbitraries.frequencyOf(
        Tuple.of(
            1,
            Arbitraries.of(
                Long.MIN_VALUE,
                Long.MIN_VALUE + 1,
                -2L,
                -1L,
                0L,
                1L,
                (long) Integer.MAX_VALUE,
                (long) Integer.MAX_VALUE + 1,
                0xFFFF_FFFFL,
                Long.MAX_VALUE - 3,
                Long.MAX_VALUE)),
        Tuple.of(1, Arbitraries.longs()));
  }

  /** A publisher's single frame, or a rollup's frames of up to a few entries. */
  @Provide
  Arbitrary<Long> frameTargets() {
    return Arbitraries.frequencyOf(
        Tuple.of(1, Arbitraries.just(Long.MAX_VALUE)),
        Tuple.of(3, Arbitraries.longs().between(1, 10_000)));
  }

  private static List<Entry> append(List<Spec> specs) {
    return specs.stream()
        .map(
            spec ->
                switch (spec) {
                  case Spec.Initial() -> journal.initial();
                  case Spec.Configuration() -> journal.configuration();
                  case Spec.Records(int count, int dataLength) ->
                      journal.records(count, dataLength);
                })
        .toList();
  }

  private static byte[] write(List<Entry> entries, long frameTarget) throws IOException {
    final var out = new ByteArrayOutputStream();
    try (var writer = new SegmentWriter(out, IDENTITY, HISTORY, level(frameTarget), frameTarget)) {
      for (var entry : entries) {
        writer.append(entry);
      }
      writer.finish(entries.stream().anyMatch(Entry::hasRecords) ? NOON : NO_POSITION);
    }
    return out.toByteArray();
  }

  private static Level level(long frameTarget) {
    return frameTarget == Long.MAX_VALUE ? Level.PUBLISHER : Level.ROLLUP;
  }

  /** The bytes the writer counts towards a frame's target. */
  private static long bytes(List<Entry> entries) {
    return entries.stream()
        .mapToLong(e -> SegmentWriter.ENTRY_PREFIX_LENGTH + e.serializedRecord().length)
        .sum();
  }

  /** The first record's position, or 1 if there are no records. */
  private static long firstPosition(List<Entry> entries) {
    return entries.stream()
        .filter(Entry::hasRecords)
        .findFirst()
        .map(Entry::firstPosition)
        .orElse(1L);
  }

  private static long lastPosition(List<Entry> entries) {
    return entries.stream()
        .filter(Entry::hasRecords)
        .reduce((a, b) -> b)
        .map(Entry::lastPosition)
        .orElse(NO_POSITION);
  }

  /** A segment as a reader reads it: its tail first, then the body from a frame. */
  private record Read(byte[] segment, Footer footer, Metadata metadata) {

    static Read of(byte[] segment) throws TailTooShortException {
      final long tailStart = Math.max(0, segment.length - Footer.USUAL_TAIL_LENGTH);
      final var footer = Footer.read(suffix(segment, tailStart));
      Metadata metadata;
      try {
        metadata = Metadata.read(suffix(segment, tailStart), tailStart, footer);
      } catch (TailTooShortException e) {
        final long metadataStart = e.metadataStart();
        metadata = Metadata.read(suffix(segment, metadataStart), metadataStart, footer);
      }
      return new Read(segment, footer, metadata);
    }

    /** The entries from {@code offset} to the body's end, as a ranged GET returns them. */
    List<Entry> entriesFrom(long offset) throws IOException {
      final int end = (int) Math.min(footer.bodyLength(), segment.length);
      final var range = Arrays.copyOfRange(segment, (int) Math.min(offset, end), end);
      final var entries = new ArrayList<Entry>();
      try (var reader = new EntryReader(new ByteArrayInputStream(range))) {
        for (var entry = reader.next(); entry != null; entry = reader.next()) {
          entries.add(entry);
        }
      }
      return entries;
    }

    private static ByteBuffer suffix(byte[] segment, long start) {
      return ByteBuffer.wrap(segment, (int) start, segment.length - (int) start).slice();
    }
  }

  /** An entry to append to the journal. */
  sealed interface Spec {

    record Initial() implements Spec {}

    record Configuration() implements Spec {}

    record Records(int count, int dataLength) implements Spec {}
  }

  /** How bytes of a segment get damaged: in place, or by losing its end. */
  enum Damage {
    FLIP_A_BYTE {
      @Override
      byte[] apply(byte[] segment, int at, long value) {
        final var damaged = segment.clone();
        damaged[at] ^= (byte) (Math.floorMod(value, 255) + 1);
        return damaged;
      }
    },
    OVERWRITE_EIGHT_BYTES {
      @Override
      byte[] apply(byte[] segment, int at, long value) {
        final var damaged = segment.clone();
        final var bytes = ByteBuffer.allocate(8).putLong(value).array();
        System.arraycopy(bytes, 0, damaged, at, Math.min(8, damaged.length - at));
        return damaged;
      }
    },
    /** One of the footer's six 8-byte numbers, which jqwik often sets to extremes. */
    OVERWRITE_A_FOOTER_NUMBER {
      @Override
      byte[] apply(byte[] segment, int at, long value) {
        final var damaged = segment.clone();
        final int field = 16 + 8 * Math.floorMod(at, 6);
        ByteBuffer.wrap(damaged)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(damaged.length - Footer.LENGTH + field, value);
        return damaged;
      }
    },
    CUT_THE_END {
      @Override
      byte[] apply(byte[] segment, int at, long value) {
        return Arrays.copyOf(segment, at);
      }
    };

    abstract byte[] apply(byte[] segment, int at, long value);
  }
}
