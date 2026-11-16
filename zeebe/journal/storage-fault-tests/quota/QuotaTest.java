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
 * Write-back client with an exhausted quota (modelled by the patched LazyFS): while the quota
 * switch is on, fsync of segment files is acknowledged without persisting. The journal behaves
 * like a follower: append -> flush -> acknowledge.
 *
 * <p>run <dir> <insegment|rollover> <close|halt> <quotaFile>; verify <dir>
 */
public final class QuotaTest {
  static final PrintStream OUT = new PrintStream(System.out, true);
  static final int SEGMENT = 64 * 1024;
  static final int ENTRY = 1000;

  public static void main(final String[] args) throws Exception {
    final Path dir = Path.of(args[1]);
    if (args[0].equals("run")) {
      run(dir, args[2], args[3], Path.of(args[4]));
    } else {
      verify(dir);
    }
  }

  static long freeSpace = 0;

  static SegmentedJournal open(final Path dir, final MetaStore meta) throws IOException {
    Files.createDirectories(dir.resolve("data"));
    return SegmentedJournal.builder(new SimpleMeterRegistry())
        .withDirectory(dir.resolve("data").toFile())
        .withName("journal")
        .withMaxSegmentSize(SEGMENT)
        .withJournalIndexDensity(1)
        .withFreeDiskSpace(freeSpace)
        .withSegmentAllocator(SegmentAllocator.fill()) // 8.8 default: FILL
        .withMetaStore(meta)
        .build();
  }

  static void run(final Path dir, final String scenario, final String end, final Path quotaFile)
      throws Exception {
    Files.createDirectories(dir);
    final var meta = new MetaStore(dir.resolve("journal.meta"));
    final boolean nextFails = scenario.startsWith("nextfail");
    if (nextFails) {
      // the volume is close to full: limit just below what is available now
      freeSpace = Files.getFileStore(dir).getUsableSpace() - 64L * 1024 * 1024;
    }
    final var journal = open(dir, meta);
    if (nextFails) {
      // 1 -> 2 rolls over normally and pre-creates segment 3
      while (segments(journal) < 2) {
        appendAndAck(journal);
      }
      Thread.sleep(500); // let the background preparation of segment 3 finish
      // the disk fills up while segment 2 is active
      final Path ballast = dir.resolve("ballast");
      try (final var ch =
          FileChannel.open(ballast, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
        final var chunk = ByteBuffer.allocate(1024 * 1024);
        for (int i = 0; i < 128; i++) {
          chunk.clear();
          ch.write(chunk);
        }
        ch.force(true);
      }
      OUT.println(
          "DISK nearly full: usable=" + Files.getFileStore(dir).getUsableSpace() + " limit=" + freeSpace);
      // 2 -> 3 uses the pre-created segment; preparing segment 4 fails for lack of space
      while (segments(journal) < 3) {
        appendAndAck(journal);
      }
      for (int i = 0; i < 5; i++) {
        appendAndAck(journal);
      }
      Thread.sleep(500);
      try (final var files = Files.list(dir.resolve("data"))) {
        OUT.println("DISK segment files: " + files.map(f -> f.getFileName().toString()).sorted().toList());
      }
    } else {
      while (journal.getLastIndex() < 100) {
        appendAndAck(journal);
      }
    }
    final int segmentsBefore = segments(journal);
    final long durable = journal.getLastIndex();
    Files.writeString(quotaFile, "\\.log$\n");
    OUT.println("QUOTA_ON after index " + durable + " (segments=" + segmentsBefore + ")");
    if (scenario.startsWith("nextfail")) {
      final int n = Integer.parseInt(scenario.substring("nextfail".length()));
      for (int i = 0; i < n; i++) {
        appendAndAck(journal);
      }
      OUT.println("segments now " + segments(journal) + " (no new segment possible)");
    } else if (scenario.equals("insegment")) {
      for (int i = 0; i < 10 && segments(journal) == segmentsBefore; i++) {
        appendAndAck(journal);
      }
      OUT.println("segments now " + segments(journal) + " (expected unchanged)");
    } else {
      while (segments(journal) == segmentsBefore) {
        appendAndAck(journal);
      }
      for (int i = 0; i < 3; i++) {
        appendAndAck(journal);
      }
      OUT.println("segments now " + segments(journal) + " (rolled over)");
    }
    OUT.println("LAST_ACK " + journal.getLastIndex() + " stored=" + meta.loadLastFlushedIndex());
    if (end.equals("close")) {
      journal.close();
      meta.close(); // like Raft's MetaStore.close(): forces the meta file
      OUT.println("CLOSED gracefully");
    } else {
      OUT.println("HALT");
      Runtime.getRuntime().halt(0);
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
      try (JournalReader reader = journal.openReader()) {
        while (reader.hasNext()) {
          reader.next();
          count++;
        }
      }
      OUT.println(
          "REOPEN OK storedFlushedIndex=%d lastIndex=%d entries=%d"
              .formatted(stored, journal.getLastIndex(), count));
      journal.close();
    } catch (final Throwable t) {
      Throwable c = t;
      final var sb = new StringBuilder();
      while (c != null) {
        sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append(" | ");
        c = c.getCause();
      }
      OUT.println("REOPEN FAILED storedFlushedIndex=" + stored + ": " + sb);
    }
  }

  /** Like Raft's MetaStore: unforced write while running, forced on close. */
  static final class MetaStore implements JournalMetaStore {
    private final FileChannel channel;
    private long value;

    MetaStore(final Path file) {
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
