/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class SegmentedJournalUnmapCrashHarness {

  private static final int ENTRIES_PER_SEGMENT = 4;
  private static final int ATTEMPTS = 2000;
  private static final int READER_THREADS = 16;

  private SegmentedJournalUnmapCrashHarness() {}

  public static void main(final String[] args) throws Exception {
    final var data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
    final var recordDataWriter = new DirectBufferWriter(data);
    final int entrySize = FrameUtil.getLength() + getSerializedSize(data);
    final var base = Files.createTempDirectory("unmap-crash");

    for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
      final var journal =
          SegmentedJournal.builder(new SimpleMeterRegistry())
              .withDirectory(base.resolve("j" + attempt).toFile())
              .withMaxSegmentSize(
                  entrySize * ENTRIES_PER_SEGMENT
                      + SegmentDescriptorSerializer.currentEncodingLength())
              .withJournalIndexDensity(ENTRIES_PER_SEGMENT / 2)
              .withMetaStore(new MockJournalMetastore())
              .build();
      for (int i = 1; i <= ENTRIES_PER_SEGMENT * 8; i++) {
        journal.append(i, recordDataWriter);
      }

      final var go = new AtomicBoolean(false);
      final var threads = new ArrayList<Thread>();
      final var readers = new ArrayList<JournalReader>();
      for (int t = 0; t < READER_THREADS; t++) {
        final var reader = journal.openReader();
        readers.add(reader);
        final var thread =
            new Thread(
                () -> {
                  while (!go.get()) {
                    Thread.onSpinWait();
                  }
                  // Hammer the mapped buffer with reads. If close() unmaps a buffer mid-read, this
                  // dereferences freed memory.
                  for (int i = 0; i < 100_000; i++) {
                    try {
                      reader.seekToFirst();
                      while (reader.hasNext()) {
                        reader.next();
                      }
                    } catch (final Throwable ignored) {
                    }
                  }
                });
        thread.setDaemon(true);
        threads.add(thread);
        thread.start();
      }

      go.set(true);
      // let the readers get deep into buffer reads, then pull the mapping out from under them
      Thread.sleep(1);
      journal.close();

      for (final var thread : threads) {
        thread.interrupt();
      }
      for (final var reader : readers) {
        try {
          reader.close();
        } catch (final Throwable ignored) {
          // reader may already be torn down
        }
      }
      if (attempt % 200 == 0) {
        System.out.println("attempt " + attempt + " survived");
      }
    }

    System.out.println("HARNESS_COMPLETED_NO_CRASH");
  }

  private static int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(Long.MAX_VALUE, Long.MAX_VALUE, data);
    final var serializer = new SBESerializer();
    final var buffer = ByteBuffer.allocate(128);
    final var maybeWritten = serializer.writeData(record, new UnsafeBuffer(buffer), 0);
    return maybeWritten.get() + serializer.getMetadataLength();
  }
}
