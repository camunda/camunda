/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.file.SegmentAllocator;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Crash-consistency harness for the Zeebe journal on LazyFS.
 *
 * <p>run <dir> <scenario> <seed> <metaMode>: performs operations and prints durability promises:
 * "ACK n" after a successful flush (entries up to n must survive), "TRUNC i" after deleteAfter(i)
 * (entries after i are gone, entries up to i are claimed flushed), "RESET n" after reset(n). Prints
 * "READY" when a deterministic scenario finished, then blocks until it is killed.
 *
 * <p>verify <dir> <metaMode>: opens the journal and checks all readable entries.
 */
public final class JournalCrash {
  static final int SEGMENT_SIZE = 16 * 1024;
  static final PrintStream OUT = new PrintStream(System.out, true);

  public static void main(final String[] args) throws Exception {
    final Path dir = Path.of(args[1]);
    switch (args[0]) {
      case "run" -> run(dir, args[2], Long.parseLong(args[3]), args[4]);
      case "verify" -> verify(dir, args[2]);
      default -> throw new IllegalArgumentException(args[0]);
    }
  }

  static SegmentedJournal open(final Path dir, final String metaMode) throws IOException {
    return open(dir, metaMode, Integer.getInteger("journal.segmentSize", SEGMENT_SIZE));
  }

  static SegmentedJournal open(final Path dir, final String metaMode, final int segmentSize)
      throws IOException {
    Files.createDirectories(dir.resolve("data"));
    return SegmentedJournal.builder(new SimpleMeterRegistry())
        .withDirectory(dir.resolve("data").toFile())
        .withName("journal")
        .withMaxSegmentSize(segmentSize)
        .withJournalIndexDensity(1)
        .withFreeDiskSpace(0)
        .withSegmentAllocator(SegmentAllocator.fill())
        .withMetaStore(new FileMetaStore(dir.resolve("journal.meta"), metaMode.equals("forced")))
        .build();
  }

  static void run(final Path dir, final String scenario, final long seed, final String metaMode)
      throws Exception {
    final var random = new Random(seed);
    final var journal = open(dir, metaMode);
    switch (scenario) {
      case "follower" -> {
        while (true) {
          appendBatch(journal, random, 1 + random.nextInt(20));
          journal.flush();
          OUT.println("ACK " + journal.getLastIndex());
        }
      }
      case "leader-truncate" -> leaderTruncate(journal);
      case "mixed" -> {
        while (true) {
          final int op = random.nextInt(100);
          if (op < 55) { // follower: append and acknowledge
            appendBatch(journal, random, 1 + random.nextInt(20));
            journal.flush();
            OUT.println("ACK " + journal.getLastIndex());
          } else if (op < 85) { // leader: append without flushing
            appendBatch(journal, random, 1 + random.nextInt(30));
          } else if (op < 98) { // follower truncates a conflicting suffix
            final long first = journal.getFirstIndex();
            final long last = journal.getLastIndex();
            if (last > first + 1) {
              final long to = first + random.nextLong(last - first);
              journal.deleteAfter(to);
              journal.flush(); // RaftLog.deleteAfter flushes afterwards
              OUT.println("TRUNC " + to);
            }
          } else { // snapshot install: reset the log
            final long next = journal.getLastIndex() + 1 + random.nextInt(50);
            journal.reset(next);
            OUT.println("RESET " + next);
          }
        }
      }
      default -> throw new IllegalArgumentException(scenario);
    }
  }

  /** The deleteAfter case: unflushed entries span the end of one segment and the next one. */
  static void leaderTruncate(final SegmentedJournal journal) throws Exception {
    final var random = new Random(42);
    // fill until we are in the second segment, flushing everything (committed entries)
    while (segmentStarts(journal).size() < 2) {
      appendBatch(journal, random, 1);
      journal.flush();
    }
    // go back to having the flushed index in the middle of a segment P: append and flush until
    // a fresh segment starts, then flush only part of the next segment
    final int segmentsBefore = segmentStarts(journal).size();
    while (segmentStarts(journal).size() == segmentsBefore) {
      appendBatch(journal, random, 1);
      journal.flush();
    }
    final long pStart = segmentStarts(journal).getLast();
    for (int i = 0; i < 10; i++) {
      appendBatch(journal, random, 1);
    }
    journal.flush();
    final long committed = journal.getLastIndex();
    OUT.println("ACK " + committed);
    // leader tail: keep appending WITHOUT flushing until the next segment Q has a few entries
    while (segmentStarts(journal).getLast() == pStart) {
      appendBatch(journal, random, 1);
    }
    final long qStart = segmentStarts(journal).getLast();
    for (int i = 0; i < 5; i++) {
      appendBatch(journal, random, 1);
    }
    // new leader conflicts at qStart + 3: truncate after qStart + 2, as PassiveRole does
    final long truncateTo = qStart + 2;
    journal.deleteAfter(truncateTo);
    journal.flush();
    OUT.println(
        "INFO P starts at %d, committed %d, Q starts at %d, truncated after %d"
            .formatted(pStart, committed, qStart, truncateTo));
    OUT.println("TRUNC " + truncateTo);
    OUT.println("READY");
    Thread.sleep(Long.MAX_VALUE);
  }

  static List<Long> segmentStarts(final SegmentedJournal journal) {
    return new ArrayList<>(journal.getTailSegments(journal.getFirstIndex()).keySet());
  }

  static void appendBatch(final SegmentedJournal journal, final Random random, final int count) {
    for (int i = 0; i < count; i++) {
      final long index = journal.getLastIndex() + 1;
      journal.append(new DirectBufferWriter().wrap(new UnsafeBuffer(payload(index, random))));
    }
  }

  /** Payload whose content is derived from the index, so it can be checked after a crash. */
  static byte[] payload(final long index, final Random random) {
    final int size = 64 + (random == null ? 0 : random.nextInt(900));
    final var bytes = new byte[size];
    final var buf = ByteBuffer.wrap(bytes);
    buf.putLong(index);
    buf.putInt(size);
    for (int i = 12; i < size; i++) {
      bytes[i] = (byte) (index * 31 + i);
    }
    return bytes;
  }

  static void verify(final Path dir, final String metaMode) throws IOException {
    final SegmentedJournal journal;
    try {
      journal = open(dir, metaMode);
    } catch (final Exception e) {
      OUT.println("OPEN_FAIL " + rootMessage(e));
      return;
    }
    long count = 0;
    long badContent = 0;
    long expected = journal.getFirstIndex();
    try (JournalReader reader = journal.openReader()) {
      while (reader.hasNext()) {
        final JournalRecord record = reader.next();
        if (record.index() != expected) {
          OUT.println("INDEX_GAP expected " + expected + " got " + record.index());
          break;
        }
        final var data = record.data();
        final long embedded = data.getLong(0, java.nio.ByteOrder.BIG_ENDIAN);
        final int size = data.getInt(8, java.nio.ByteOrder.BIG_ENDIAN);
        if (embedded != record.index() || size != data.capacity()) {
          badContent++;
        } else {
          for (int i = 12; i < size; i++) {
            if (data.getByte(i) != (byte) (record.index() * 31 + i)) {
              badContent++;
              break;
            }
          }
        }
        count++;
        expected++;
      }
    } catch (final Exception e) {
      OUT.println("READ_FAIL at " + expected + ": " + rootMessage(e));
    }
    final var meta = new FileMetaStore(dir.resolve("journal.meta"), false);
    OUT.println(
        "OPEN_OK first=%d last=%d entries=%d badContent=%d metaFlushed=%d"
            .formatted(
                journal.getFirstIndex(),
                journal.getLastIndex(),
                count,
                badContent,
                meta.loadLastFlushedIndex()));
    journal.close();
  }

  static String rootMessage(final Throwable e) {
    Throwable t = e;
    final var sb = new StringBuilder();
    while (t != null) {
      sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
      t = t.getCause();
    }
    return sb.toString();
  }

  /**
   * Stores the last flushed index like Raft's MetaStore: an 8-byte write through a FileChannel,
   * not forced unless {@code force} is set (which models a term/vote update forcing the file).
   */
  static final class FileMetaStore implements JournalMetaStore {
    static final long NULL = -1L;
    private final FileChannel channel;
    private final boolean force;
    private long value;

    FileMetaStore(final Path file, final boolean force) {
      this.force = force;
      try {
        channel =
            FileChannel.open(
                file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        final var buf = ByteBuffer.allocate(8);
        value = channel.read(buf, 0) == 8 ? buf.flip().getLong() : NULL;
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void storeLastFlushedIndex(final long index) {
      value = index;
      try {
        channel.write(ByteBuffer.allocate(8).putLong(0, index), 0);
        if (force) {
          channel.force(true);
        }
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public long loadLastFlushedIndex() {
      return value;
    }

    @Override
    public void resetLastFlushedIndex() {
      storeLastFlushedIndex(NULL);
    }

    @Override
    public boolean hasLastFlushedIndex() {
      return value != NULL;
    }
  }
}
