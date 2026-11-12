/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.db.writebehind.TestValues.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Readers copy held values out while the writer flushes the same values and the cache fills, so the
 * store's objects are used by several threads at once. A msgpack object that two threads encode at
 * the same time corrupts the output, as it reuses its writer; held values must never be encoded
 * again, so every value read here must be one that was committed, whole.
 */
final class ConcurrentCopyOutTest {

  private static final int KEYS = 8;
  private static final int COMMITS = 5_000;
  private static final int READERS = 3;

  private final InMemoryRawDbAccess storage = new InMemoryRawDbAccess();
  // a small cache, so that reads also load from the storage and fill the cache
  private final WriteBehindStore<RawKey, HeldValue, DbValue> store =
      new WriteBehindStore<>(
          new RawBackend(storage), WriteBehindConfiguration.defaults().withCacheMaxBytes(2_048));
  private final ExecutorService readers = Executors.newFixedThreadPool(READERS);

  @AfterEach
  void tearDown() throws InterruptedException {
    readers.shutdownNow();
    readers.awaitTermination(30, TimeUnit.SECONDS);
    store.close();
  }

  @Test
  void shouldReadOnlyWholeCommittedValuesWhileTheWriterFlushes() throws Exception {
    // given
    final var done = new AtomicBoolean();
    final List<Future<Long>> reads = new ArrayList<>();
    for (int i = 0; i < READERS; i++) {
      reads.add(readers.submit(() -> readUntil(done)));
    }

    // when
    final var scratch = new ExpandableArrayBuffer();
    for (int n = 1; n <= COMMITS; n++) {
      final var key = key(n % KEYS);
      store.commit(new RawKey[] {key}, new HeldValue[] {Values.hold(record(n), scratch)});
    }
    store.awaitFlushed();
    done.set(true);

    // then
    for (final var read : reads) {
      assertThat(read.get(30, TimeUnit.SECONDS)).isPositive();
    }
    for (int id = 0; id < KEYS; id++) {
      final var bytes = key(id).bytes();
      final var stored =
          (TestRecord) Values.decode(storage.get(bytes, bytes.length), new TestRecord()).object();
      assertWhole(stored);
      assertThat(stored.counter() % KEYS).isEqualTo(id);
    }
  }

  /** Reads all keys until {@code done}; fails on a value that was never committed. */
  private long readUntil(final AtomicBoolean done) {
    final var target = new TestRecord();
    long found = 0;
    while (!done.get()) {
      for (int id = 0; id < KEYS; id++) {
        final var held = store.get(key(id), target);
        if (held != null) {
          Values.copyInto(held, target);
          assertWhole(target);
          found++;
        }
      }
    }
    return found;
  }

  private static TestRecord record(final long n) {
    return TestRecord.of(name(n), n);
  }

  /** A name whose length depends on the counter, so that mixed-up encodings show. */
  private static String name(final long n) {
    return "v".repeat((int) (n % 64)) + n;
  }

  private static void assertWhole(final TestRecord record) {
    assertThat(record.name()).isEqualTo(name(record.counter()));
  }
}
