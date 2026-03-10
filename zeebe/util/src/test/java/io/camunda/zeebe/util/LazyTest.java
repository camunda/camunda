/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LazyTest {

  @Nested
  class Get {

    @Test
    void shouldComputeOnFirstAccess() {
      // given
      final var lazy = Lazy.of(() -> "hello");

      // when
      final var result = lazy.get();

      // then
      assertThat(result).isEqualTo("hello");
    }

    @Test
    void shouldCacheAfterFirstAccess() {
      // given
      final var callCount = new AtomicInteger();
      final var lazy =
          Lazy.of(
              () -> {
                callCount.incrementAndGet();
                return "cached";
              });

      // when
      lazy.get();
      lazy.get();
      lazy.get();

      // then
      assertThat(callCount).hasValue(1);
    }

    @Test
    void shouldSupportNullValue() {
      // given
      final var lazy = Lazy.of(() -> null);

      // when
      final var result = lazy.get();

      // then
      assertThat(result).isNull();
    }

    @Test
    void shouldRejectNullSupplier() {
      assertThatThrownBy(() -> Lazy.of(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  class IsEvaluated {

    @Test
    void shouldBeFalseBeforeAccess() {
      // given
      final var lazy = Lazy.of(() -> "value");

      // then
      assertThat(lazy.isEvaluated()).isFalse();
    }

    @Test
    void shouldBeTrueAfterAccess() {
      // given
      final var lazy = Lazy.of(() -> "value");

      // when
      lazy.get();

      // then
      assertThat(lazy.isEvaluated()).isTrue();
    }
  }

  @Nested
  class EqualsAndHashCode {

    @Test
    void shouldBeEqualWhenValuesEqual() {
      // given
      final var a = Lazy.of(() -> "same");
      final var b = Lazy.of(() -> "same");

      // then
      assertThat(a).isEqualTo(b);
    }

    @Test
    void shouldNotBeEqualWhenValuesDiffer() {
      // given
      final var a = Lazy.of(() -> "one");
      final var b = Lazy.of(() -> "two");

      // then
      assertThat(a).isNotEqualTo(b);
    }

    @Test
    void shouldHaveConsistentHashCode() {
      // given
      final var a = Lazy.of(() -> "same");
      final var b = Lazy.of(() -> "same");

      // then
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldHandleNullValues() {
      // given
      final var a = Lazy.of(() -> null);
      final var b = Lazy.of(() -> null);

      // then
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void shouldNotBeEqualToNonLazy() {
      // given
      final var lazy = Lazy.of(() -> "value");

      // then
      assertThat(lazy).isNotEqualTo("value");
    }
  }

  @Nested
  class ToString {

    @Test
    void shouldDelegateToValue() {
      // given
      final var lazy = Lazy.of(() -> 42);

      // then
      assertThat(lazy).hasToString("42");
    }

    @Test
    void shouldHandleNullValue() {
      // given
      final var lazy = Lazy.of(() -> null);

      // then
      assertThat(lazy).hasToString("null");
    }
  }

  @Nested
  class ThreadSafety {

    @Test
    void shouldReturnSameValueAcrossThreads() throws InterruptedException {
      // given
      final var lazy = Lazy.of(() -> "shared");
      final var threadCount = 16;
      final var startLatch = new CountDownLatch(1);
      final var doneLatch = new CountDownLatch(threadCount);
      final var results = new CopyOnWriteArrayList<String>();

      // when
      IntStream.range(0, threadCount)
          .forEach(
              i -> {
                final var thread =
                    new Thread(
                        () -> {
                          try {
                            startLatch.await();
                            results.add(lazy.get());
                          } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                          } finally {
                            doneLatch.countDown();
                          }
                        });
                thread.start();
              });

      startLatch.countDown();
      doneLatch.await();

      // then
      assertThat(results).hasSize(threadCount).containsOnly("shared");
    }

    @Test
    void shouldCacheNullAcrossThreads() throws InterruptedException {
      // given
      final var callCount = new AtomicInteger();
      final var lazy =
          Lazy.of(
              () -> {
                callCount.incrementAndGet();
                return null;
              });
      final var threadCount = 16;
      final var startLatch = new CountDownLatch(1);
      final var doneLatch = new CountDownLatch(threadCount);
      final List<Object> results = new CopyOnWriteArrayList<>();

      // when
      IntStream.range(0, threadCount)
          .forEach(
              i -> {
                final var thread =
                    new Thread(
                        () -> {
                          try {
                            startLatch.await();
                            // Use sentinel to distinguish null result from "no result"
                            final var result = lazy.get();
                            results.add(result == null ? "NULL_SENTINEL" : result);
                          } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                          } finally {
                            doneLatch.countDown();
                          }
                        });
                thread.start();
              });

      startLatch.countDown();
      doneLatch.await();

      // then
      assertThat(results).hasSize(threadCount).containsOnly("NULL_SENTINEL");
    }
  }
}
