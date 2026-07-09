/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
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
    final var reference = new SecretReference("token");

    // when
    final Optional<String> value = cache.get(reference);

    // then
    assertThat(value).isEmpty();
  }

  @Test
  void shouldReturnValueAfterPut() {
    // given
    final var reference = new SecretReference("token");
    cache.put(reference, "secret-value");

    // when
    final Optional<String> value = cache.get(reference);

    // then
    assertThat(value).contains("secret-value");
  }

  @Test
  void shouldReturnValueForEqualReference() {
    // given a reference is looked up by value, not identity
    cache.put(new SecretReference("token"), "secret-value");

    // when
    final Optional<String> value = cache.get(new SecretReference("token"));

    // then
    assertThat(value).contains("secret-value");
  }

  @Test
  void shouldOverwriteValueOnSecondPut() {
    // given
    final var reference = new SecretReference("token");
    cache.put(reference, "old-value");

    // when
    cache.put(reference, "new-value");

    // then
    assertThat(cache.get(reference)).contains("new-value");
  }

  @Test
  void shouldIsolateDistinctReferences() {
    // given
    cache.put(new SecretReference("token"), "token-value");
    cache.put(new SecretReference("apiKey"), "api-key-value");

    // when / then
    assertThat(cache.get(new SecretReference("token"))).contains("token-value");
    assertThat(cache.get(new SecretReference("apiKey"))).contains("api-key-value");
    assertThat(cache.get(new SecretReference("unknown"))).isEmpty();
  }

  @Test
  void shouldRejectNullValue() {
    // given
    final var reference = new SecretReference("token");

    // when / then — a resolved value is never null; the cache guards against it
    assertThatThrownBy(() -> cache.put(reference, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldServeConcurrentPutsAndGets() throws InterruptedException {
    // given a broker-shared cache is written from many threads at once
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
                          cache.put(
                              new SecretReference("secret-" + t + "-" + i), "value-" + t + "-" + i);
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
        assertThat(cache.get(new SecretReference("secret-" + t + "-" + i)))
            .contains("value-" + t + "-" + i);
      }
    }
  }

  @Test
  void shouldIsolateValuesByPhysicalTenant() {
    // given the same secret name resolves to different values in two physical tenants
    final var reference = new SecretReference("token");
    final var tenantA = cache.withPhysicalTenant("tenant-a");
    final var tenantB = cache.withPhysicalTenant("tenant-b");
    tenantA.put(reference, "value-a");
    tenantB.put(reference, "value-b");

    // when / then each tenant's view sees only its own value
    assertThat(tenantA.get(reference)).contains("value-a");
    assertThat(tenantB.get(reference)).contains("value-b");
  }

  @Test
  void shouldShareEntriesAcrossViewsOfSameTenant() {
    // given a value stored through one view of a physical tenant
    final var reference = new SecretReference("token");
    cache.withPhysicalTenant("tenant-a").put(reference, "value-a");

    // when / then another view of the same tenant reads it from the shared backing store
    assertThat(cache.withPhysicalTenant("tenant-a").get(reference)).contains("value-a");
  }
}
