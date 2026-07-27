/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class InMemorySecretCacheTest {

  private final SecretCache cache = new InMemorySecretCache();

  @Test
  void shouldReturnEmptyWhenReferenceNotCached() {
    // given
    final var reference = "token";

    // when
    final Optional<String> value = cache.get(reference);

    // then
    assertThat(value).isEmpty();
  }

  @Test
  void shouldReturnValueAfterPut() {
    // given
    final var reference = "token";
    cache.put(reference, "secret-value");

    // when
    final Optional<String> value = cache.get(reference);

    // then
    assertThat(value).contains("secret-value");
  }

  @Test
  void shouldReturnValueForEqualReference() {
    // given a reference is looked up by value, not identity
    cache.put("token", "secret-value");

    // when
    final Optional<String> value = cache.get("token");

    // then
    assertThat(value).contains("secret-value");
  }

  @Test
  void shouldOverwriteValueOnSecondPut() {
    // given
    final var reference = "token";
    cache.put(reference, "old-value");

    // when
    cache.put(reference, "new-value");

    // then
    assertThat(cache.get(reference)).contains("new-value");
  }

  @Test
  void shouldIsolateDistinctReferences() {
    // given
    cache.put("token", "token-value");
    cache.put("apiKey", "api-key-value");

    // when / then
    assertThat(cache.get("token")).contains("token-value");
    assertThat(cache.get("apiKey")).contains("api-key-value");
    assertThat(cache.get("unknown")).isEmpty();
  }

  @Test
  void shouldRejectNullValue() {
    // given
    final var reference = "token";

    // when / then — a resolved value is never null; the cache guards against it
    assertThatThrownBy(() -> cache.put(reference, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldServeConcurrentPutsAndGets() throws InterruptedException {
    // given the cache is written from many threads at once
    final int threads = 16;
    final int entriesPerThread = 500;
    final var executor = Executors.newFixedThreadPool(threads);
    final var start = new CountDownLatch(1);
    final var done = new CountDownLatch(threads);

    // when each thread stores its own entries concurrently
    IntStream.range(0, threads)
        .forEach(
            t ->
                executor.execute(
                    () -> {
                      try {
                        start.await();
                        for (int i = 0; i < entriesPerThread; i++) {
                          cache.put("secret-" + t + "-" + i, "value-" + t + "-" + i);
                        }
                      } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                      } finally {
                        done.countDown();
                      }
                    }));
    start.countDown();
    final boolean finished = done.await(30, TimeUnit.SECONDS);
    executor.shutdownNow();

    // then every entry is present with the exact value its writer stored
    assertThat(finished).isTrue();
    for (int t = 0; t < threads; t++) {
      for (int i = 0; i < entriesPerThread; i++) {
        assertThat(cache.get("secret-" + t + "-" + i)).contains("value-" + t + "-" + i);
      }
    }
  }
}
