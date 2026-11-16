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
 * The 8.8 journal on a real write-back filesystem (JuiceFS --writeback) whose directory quota is
 * exhausted. Follower sequence: append -> flush -> acknowledge; everything thrown is reported.
 *
 * <p>run <dir> <entriesWhileFull>; verify <dir>
 */
public final class JfsTest {
  static final PrintStream OUT = new PrintStream(System.out, true);
  static final int SEGMENT = 4 * 1024 * 1024;
  static final int ENTRY = 2000;

  public static void main(final String[] args) throws Exception {
    final Path dir = Path.of(args[1]);
    if (args[0].equals("run")) {
      run(dir, Integer.parseInt(args[2]));
    } else {
      verify(dir);
    }
  }

  static SegmentedJournal open(final Path dir, final MetaStore meta) throws IOException {
    Files.createDirectories(dir.resolve("data"));
    return SegmentedJournal.builder(new SimpleMeterRegistry())
        .withDirectory(dir.resolve("data").toFile())
        .withName("journal")
        .withMaxSegmentSize(SEGMENT)
        .withJournalIndexDensity(1)
        .withFreeDiskSpace(0)
        .withSegmentAllocator(SegmentAllocator.fill()) // 8.8 default
        .withMetaStore(meta)
        .build();
  }

  static void run(final Path dir, final int whileFull) throws Exception {
    Files.createDirectories(dir);
    final var meta = new MetaStore(dir.resolve("journal.meta"));
    final var journal = open(dir, meta);
    while (segments(journal) < 2) {
      appendAndAck(journal);
    }
    for (int i = 0; i < 20; i++) {
      appendAndAck(journal);
    }
    Thread.sleep(1000); // background preparation of segment 3
    OUT.println("BEFORE FULL lastIndex=" + journal.getLastIndex() + " files=" + files(dir));
    // exhaust the directory quota with a filler file
    final Path ballast = dir.getParent().resolve("ballast");
    long written = 0;
    try (final var ch =
        FileChannel.open(ballast, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      final var chunk = ByteBuffer.allocate(4 * 1024 * 1024);
      while (true) {
        chunk.clear();
        written += ch.write(chunk);
        if (written % (256L * 1024 * 1024) == 0) {
          ch.force(true);
        }
      }
    } catch (final IOException e) {
      OUT.println("QUOTA EXHAUSTED after " + (written >> 20) + " MiB filler: " + e);
    }
    // keep going like a follower
    int acked = 0;
    for (int i = 0; i < whileFull + 20 && acked < whileFull; i++) {
      final long next = journal.getLastIndex() + 1;
      try {
        appendAndAck(journal);
        acked++;
      } catch (final Throwable t) {
        OUT.println("THROWN at " + next + ": " + t.getClass().getName() + ": " + t.getMessage()
            + " | lastIndex=" + journal.getLastIndex() + " stored=" + meta.loadLastFlushedIndex());
      }
    }
    OUT.println("LAST_ACK " + journal.getLastIndex() + " stored=" + meta.loadLastFlushedIndex()
        + " acknowledgedWhileFull=" + acked);
    try {
      journal.close();
      meta.close();
      OUT.println("CLOSED gracefully");
    } catch (final Throwable t) {
      OUT.println("CLOSE threw " + t);
    }
  }

  static String files(final Path dir) throws IOException {
    try (final var s = Files.list(dir.resolve("data"))) {
      return s.map(f -> f.getFileName().toString()).sorted().toList().toString();
    }
  }

  static int segments(final SegmentedJournal journal) {
    return journal.getTailSegments(journal.getFirstIndex()).size();
  }

  static void appendAndAck(final SegmentedJournal journal) throws Exception {
    final long index = journal.getLastIndex() + 1;
    final var bytes = new byte[ENTRY];
    ByteBuffer.wrap(bytes).putLong(index);
    for (int i = 8; i < ENTRY; i++) {
      bytes[i] = (byte) (index * 31 + i);
    }
    journal.append(new DirectBufferWriter().wrap(new UnsafeBuffer(bytes)));
    journal.flush();
    OUT.println("ACK " + journal.getLastIndex());
  }

  static void verify(final Path dir) {
    final var meta = new MetaStore(dir.resolve("journal.meta"));
    final long stored = meta.loadLastFlushedIndex();
    try {
      final var journal = open(dir, meta);
      long count = 0;
      long bad = 0;
      try (JournalReader reader = journal.openReader()) {
        while (reader.hasNext()) {
          final var r = reader.next();
          if (r.data().getLong(0, java.nio.ByteOrder.BIG_ENDIAN) != r.index()) {
            bad++;
          }
          count++;
        }
      }
      OUT.println("REOPEN OK stored=%d lastIndex=%d entries=%d badContent=%d"
          .formatted(stored, journal.getLastIndex(), count, bad));
      journal.close();
    } catch (final Throwable t) {
      Throwable c = t;
      final var sb = new StringBuilder();
      while (c != null) {
        sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        c = c.getCause();
      }
      OUT.println("REOPEN FAILED stored=" + stored + ": " + sb);
    }
  }

  /** Like Raft's MetaStore: unforced write while running, forced on close. */
  static final class MetaStore implements JournalMetaStore {
    private final FileChannel channel;
    private long value;

    MetaStore(final Path file) {
      try {
        channel = FileChannel.open(
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

    void close() throws IOException {
      channel.write(ByteBuffer.allocate(8).putLong(0, value), 0);
      channel.force(true);
      channel.close();
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
