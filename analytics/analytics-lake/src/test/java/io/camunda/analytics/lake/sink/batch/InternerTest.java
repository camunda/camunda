/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.sink.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class InternerTest {

  @Test
  void shouldAssignTheSameCodeToTheSameValueOnRepeatedInterns() {
    // given
    final Interner interner = new Interner();

    // when
    final int first = interner.intern("process-instance");
    final int second = interner.intern("process-instance");
    final int third = interner.intern(new StringBuilder("process-instance"));

    // then
    assertThat(second).isEqualTo(first);
    assertThat(third).isEqualTo(first);
  }

  @Test
  void shouldAssignDistinctCodesToDistinctValues() {
    // given
    final Interner interner = new Interner();

    // when
    final int a = interner.intern("alpha");
    final int b = interner.intern("beta");

    // then
    assertThat(a).isNotEqualTo(b);
    assertThat(interner.valueOf(a)).isEqualTo("alpha");
    assertThat(interner.valueOf(b)).isEqualTo("beta");
  }

  @Test
  void shouldGrowPastInitialCapacityAndStillResolveEveryCode() {
    // given a tiny initial capacity so this test forces several growths
    final Interner interner = new Interner(2);
    final int distinctValues = 200;

    // when
    final int[] codes = new int[distinctValues];
    for (int i = 0; i < distinctValues; i++) {
      codes[i] = interner.intern("value-" + i);
    }

    // then every code still resolves to its own value, and re-interning is stable
    for (int i = 0; i < distinctValues; i++) {
      assertThat(interner.valueOf(codes[i])).isEqualTo("value-" + i);
      assertThat(interner.intern("value-" + i)).isEqualTo(codes[i]);
    }
  }

  /**
   * Concurrent-read visibility smoke test for the publication contract documented on {@link
   * Interner}: one thread continuously interns new distinct values (forcing repeated array growth),
   * publishing how many codes are valid so far via an {@link AtomicInteger}; a second thread
   * concurrently resolves every published code through {@link Interner#valueOf(int)} — with no
   * synchronization of its own beyond reading that counter — and must never observe a stale or
   * incorrect value. This does not use {@code Thread.sleep} or Awaitility (not a dependency of this
   * module); both threads spin-wait on plain field reads, which is exactly the access pattern the
   * class's javadoc claims is safe.
   */
  @Test
  void shouldPublishNewEntriesToAConcurrentReaderThreadUnderContinuousGrowth()
      throws InterruptedException {
    // given a small initial capacity so this run forces several array reallocations
    final Interner interner = new Interner(4);
    final int totalValues = 50_000;
    final AtomicInteger internedCount = new AtomicInteger(0);
    final AtomicBoolean writerDone = new AtomicBoolean(false);
    final AtomicReference<AssertionError> readerFailure = new AtomicReference<>();

    final Thread reader =
        new Thread(
            () -> {
              int resolvedUpTo = 0;
              try {
                while (!writerDone.get() || resolvedUpTo < internedCount.get()) {
                  final int publishedCount = internedCount.get();
                  while (resolvedUpTo < publishedCount) {
                    final String value = interner.valueOf(resolvedUpTo);
                    final String expected = "value-" + resolvedUpTo;
                    if (!expected.equals(value)) {
                      throw new AssertionError(
                          "code %d resolved to '%s', expected '%s'"
                              .formatted(resolvedUpTo, value, expected));
                    }
                    resolvedUpTo++;
                  }
                  Thread.onSpinWait();
                }
              } catch (final AssertionError e) {
                readerFailure.set(e);
              }
            });
    reader.start();

    // when the writer interns a growing sequence of distinct values, publishing progress as it goes
    for (int i = 0; i < totalValues; i++) {
      final int code = interner.intern("value-" + i);
      assertThat(code).isEqualTo(i);
      internedCount.set(i + 1);
    }
    writerDone.set(true);
    reader.join(Duration.ofSeconds(30).toMillis());

    // then
    assertThat(reader.isAlive()).isFalse();
    assertThat(readerFailure.get()).isNull();
  }
}
