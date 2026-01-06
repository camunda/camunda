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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ObjectSizeEstimatorTest {

  @Test
  void shouldReturnZeroForNullObject() {
    // when
    final long size = ObjectSizeEstimator.estimateSize(null);

    // then
    assertThat(size).isZero();
  }

  @Test
  void shouldEstimateSizeForString() {
    // given
    final String smallString = "hello";
    final String largeString = "a".repeat(10000);

    // when
    final long smallSize = ObjectSizeEstimator.estimateSize(smallString);
    final long largeSize = ObjectSizeEstimator.estimateSize(largeString);

    // then
    assertThat(smallSize).isPositive();
    assertThat(largeSize).isPositive();
    assertThat(largeSize).isGreaterThan(smallSize);
  }

  @Test
  void shouldEstimateSizeForPrimitiveWrapper() {
    // when
    final long intSize = ObjectSizeEstimator.estimateSize(Integer.valueOf(42));
    final long longSize = ObjectSizeEstimator.estimateSize(Long.valueOf(42L));
    final long doubleSize = ObjectSizeEstimator.estimateSize(Double.valueOf(3.14));
    final long booleanSize = ObjectSizeEstimator.estimateSize(Boolean.TRUE);

    // then
    assertThat(intSize).isPositive();
    assertThat(longSize).isPositive();
    assertThat(doubleSize).isPositive();
    assertThat(booleanSize).isPositive();
  }

  @Test
  void shouldEstimateSizeForByteArray() {
    // given
    final byte[] smallArray = new byte[100];
    final byte[] largeArray = new byte[10000];

    // when
    final long smallSize = ObjectSizeEstimator.estimateSize(smallArray);
    final long largeSize = ObjectSizeEstimator.estimateSize(largeArray);

    // then
    assertThat(smallSize).isPositive();
    assertThat(largeSize).isPositive();
    assertThat(largeSize).isGreaterThan(smallSize);
  }

  @Test
  void shouldEstimateSizeForCollection() {
    // given
    final List<String> smallList = List.of("a", "b", "c");
    final List<String> largeList = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      largeList.add("item" + i);
    }

    // when
    final long smallSize = ObjectSizeEstimator.estimateSize(smallList);
    final long largeSize = ObjectSizeEstimator.estimateSize(largeList);

    // then
    assertThat(smallSize).isPositive();
    assertThat(largeSize).isPositive();
    assertThat(largeSize).isGreaterThan(smallSize);
  }

  @Test
  void shouldEstimateSizeForMap() {
    // given
    final Map<String, Object> smallMap = Map.of("key1", "value1", "key2", 42);
    final Map<String, Object> largeMap = new HashMap<>();
    for (int i = 0; i < 1000; i++) {
      largeMap.put("key" + i, "value" + i);
    }

    // when
    final long smallSize = ObjectSizeEstimator.estimateSize(smallMap);
    final long largeSize = ObjectSizeEstimator.estimateSize(largeMap);

    // then
    assertThat(smallSize).isPositive();
    assertThat(largeSize).isPositive();
    assertThat(largeSize).isGreaterThan(smallSize);
  }

  @Test
  void shouldEstimateSizeForComplexObject() {
    // given
    final TestDataObject smallObject = new TestDataObject("name", 42, new byte[10]);
    final TestDataObject largeObject = new TestDataObject("name".repeat(1000), 42, new byte[10000]);

    // when
    final long smallSize = ObjectSizeEstimator.estimateSize(smallObject);
    final long largeSize = ObjectSizeEstimator.estimateSize(largeObject);

    // then
    assertThat(smallSize).isPositive();
    assertThat(largeSize).isPositive();
    assertThat(largeSize).isGreaterThan(smallSize);
  }

  @Test
  void shouldWorkCorrectlyUnderConcurrentAccess() throws InterruptedException {
    // given
    final int threadCount = 10;
    final int iterationsPerThread = 100;
    final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch doneLatch = new CountDownLatch(threadCount);
    final AtomicBoolean hasError = new AtomicBoolean(false);

    // when
    for (int t = 0; t < threadCount; t++) {
      final int threadId = t;
      executor.submit(
          () -> {
            try {
              startLatch.await();
              for (int i = 0; i < iterationsPerThread; i++) {
                final TestDataObject obj =
                    new TestDataObject("thread" + threadId + "_item" + i, i, new byte[100]);
                final long size = ObjectSizeEstimator.estimateSize(obj);
                if (size <= 0) {
                  hasError.set(true);
                }
              }
            } catch (final Exception e) {
              hasError.set(true);
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown(); // Start all threads simultaneously
    final boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
    executor.shutdown();

    // then
    assertThat(completed).isTrue();
    assertThat(hasError.get()).isFalse();
  }

  @Test
  void shouldThrowExceptionForNonSerializableObject() {
    // given
    final Object nonSerializable = new NonSerializableObject();

    // when/then
    assertThatThrownBy(() -> ObjectSizeEstimator.estimateSize(nonSerializable))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to estimate object size");
  }

  /** Simple test data class for serialization tests. */
  private static class TestDataObject {
    private final String name;
    private final int value;
    private final byte[] data;

    TestDataObject(final String name, final int value, final byte[] data) {
      this.name = name;
      this.value = value;
      this.data = data;
    }

    public String getName() {
      return name;
    }

    public int getValue() {
      return value;
    }

    public byte[] getData() {
      return data;
    }
  }

  /** Object that cannot be serialized by Kryo due to containing unserializable references. */
  private static final class NonSerializableObject {
    private final Thread thread = Thread.currentThread();

    public Thread getThread() {
      return thread;
    }
  }
}
