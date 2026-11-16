/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.file.SegmentAllocator;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * What does the 8.8 journal do when a write into its memory-mapped segment cannot be backed by
 * the filesystem (SIGBUS), e.g. on a full volume where preallocation did not reserve space?
 *
 * <p>write <dir> <close|halt>: follower sequence append -> flush -> "ACK", fills the volume, keeps
 * going, reports what is thrown and the journal's state, then closes gracefully or halts.
 *
 * <p>verify <dir>: reopens the journal and reports what is readable.
 */
public final class SigbusTest {
  static final PrintStream OUT = new PrintStream(System.out, true);
  static final int SEGMENT = 1024 * 1024;
  static final int ENTRY = 3000;

  public static void main(final String[] args) throws Exception {
    final Path dir = Path.of(args[1]);
    if (args[0].equals("write")) {
      write(dir, args[2]);
    } else {
      verify(dir);
    }
  }

  static SegmentedJournal open(final Path dir) throws IOException {
    Files.createDirectories(dir.resolve("data"));
    return SegmentedJournal.builder(new SimpleMeterRegistry())
        .withDirectory(dir.resolve("data").toFile())
        .withName("journal")
        .withMaxSegmentSize(SEGMENT)
        .withJournalIndexDensity(1)
        .withFreeDiskSpace(0)
        .withSegmentAllocator(SegmentAllocator.noop())
        .withMetaStore(new FileMetaStore(dir.resolve("journal.meta")))
        .build();
  }

  static void write(final Path dir, final String end) throws Exception {
    final int warmup = Integer.getInteger("warmup", 0);
    if (warmup > 0) {
      // get the append path C2-compiled on a large filesystem first, like a long-running broker
      final var warm = open(Path.of("/tmp/warm-" + System.nanoTime()));
      for (int i = 0; i < warmup; i++) {
        final long index = warm.getLastIndex() + 1;
        warm.append(new DirectBufferWriter().wrap(new UnsafeBuffer(payload(index))));
        if (i % 50 == 0) {
          warm.flush();
        }
        if (warm.getLastIndex() > 2000) {
          warm.deleteUntil(warm.getLastIndex() - 500);
        }
      }
      warm.close();
      OUT.println("WARMUP done: " + warmup + " appends");
    }
    final var journal = open(dir);
    final var meta = (FileMetaStore) metaOf(dir);
    // 1. normal operation until the current segment is about half full
    while (journal.getLastIndex() < (SEGMENT / ENTRY) / 2) {
      appendAndAck(journal);
    }
    OUT.println("STATE before full: " + state(journal, dir));
    // 2. fill the volume completely
    final Path ballast = dir.getParent().resolve("ballast");
    long ballastBytes = 0;
    try (final var out = new FileOutputStream(ballast.toFile())) {
      final var chunk = new byte[64 * 1024];
      while (true) {
        out.write(chunk);
        ballastBytes += chunk.length;
      }
    } catch (final IOException e) {
      OUT.println("VOLUME FULL after " + ballastBytes + " ballast bytes: " + e.getMessage());
    }
    // 3. keep going like a follower; report everything that is thrown
    for (int i = 0; i < 40; i++) {
      final long before = journal.getLastIndex();
      try {
        appendAndAck(journal);
      } catch (final Throwable t) {
        OUT.println(
            "THROWN at append of "
                + (before + 1)
                + ": "
                + t.getClass().getName()
                + ": "
                + t.getMessage()
                + " | "
                + state(journal, dir));
      }
    }
    OUT.println("STATE after attempts: " + state(journal, dir));
    // 4. end of the old process
    if (end.equals("close")) {
      try {
        journal.close();
        OUT.println("CLOSED gracefully");
      } catch (final Throwable t) {
        OUT.println("CLOSE threw " + t);
      }
    } else {
      OUT.println("HALT");
      Runtime.getRuntime().halt(0);
    }
    Files.deleteIfExists(ballast);
  }

  static void appendAndAck(final SegmentedJournal journal) throws Exception {
    final long index = journal.getLastIndex() + 1;
    journal.append(new DirectBufferWriter().wrap(new UnsafeBuffer(payload(index))));
    journal.flush();
    OUT.println("ACK " + journal.getLastIndex());
  }

  static byte[] payload(final long index) {
    final var bytes = new byte[ENTRY];
    ByteBuffer.wrap(bytes).putLong(index);
    for (int i = 8; i < ENTRY; i++) {
      bytes[i] = (byte) (index * 31 + i);
    }
    return bytes;
  }

  static String state(final SegmentedJournal journal, final Path dir) {
    return "journal.lastIndex="
        + journal.getLastIndex()
        + " storedFlushedIndex="
        + readStored(dir.resolve("journal.meta"));
  }

  static JournalMetaStore metaOf(final Path dir) {
    return new FileMetaStore(dir.resolve("journal.meta"));
  }

  static long readStored(final Path file) {
    try (final var ch = FileChannel.open(file, StandardOpenOption.READ)) {
      final var buf = ByteBuffer.allocate(8);
      return ch.read(buf, 0) == 8 ? buf.flip().getLong() : -1;
    } catch (final IOException e) {
      return -2;
    }
  }

  static void verify(final Path dir) throws IOException {
    final long stored = readStored(dir.resolve("journal.meta"));
    final SegmentedJournal journal;
    try {
      journal = open(dir);
    } catch (final Throwable t) {
      Throwable c = t;
      final var sb = new StringBuilder();
      while (c != null) {
        sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        c = c.getCause();
      }
      OUT.println("REOPEN FAILED (storedFlushedIndex=" + stored + "): " + sb);
      return;
    }
    long count = 0;
    long bad = 0;
    try (JournalReader reader = journal.openReader()) {
      while (reader.hasNext()) {
        final var r = reader.next();
        final var d = r.data();
        if (d.getLong(0, java.nio.ByteOrder.BIG_ENDIAN) != r.index()) {
          bad++;
        }
        count++;
      }
    }
    OUT.println(
        "REOPEN OK storedFlushedIndex=%d lastIndex=%d entries=%d badContent=%d"
            .formatted(stored, journal.getLastIndex(), count, bad));
    journal.close();
  }

  /** Like Raft's MetaStore: unforced write while running, forced on close. */
  static final class FileMetaStore implements JournalMetaStore {
    private final FileChannel channel;
    private long value;

    FileMetaStore(final Path file) {
      try {
        channel =
            FileChannel.open(
                file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        final var buf = ByteBuffer.allocate(8);
        value = channel.read(buf, 0) == 8 ? buf.flip().getLong() : -1;
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void storeLastFlushedIndex(final long index) {
      value = index;
      try {
        channel.write(ByteBuffer.allocate(8).putLong(0, index), 0);
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
      storeLastFlushedIndex(-1);
    }

    @Override
    public boolean hasLastFlushedIndex() {
      return value != -1;
    }
  }
}
