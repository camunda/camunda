/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.commons.pt.LazyPhysicalTenantResource.State;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class LazyPhysicalTenantResourceTest {

  private static final Duration BACKOFF = Duration.ofSeconds(5);

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
  private final List<Object> closed = new ArrayList<>();

  @Test
  void shouldNotCreateTheResourceBeforeItsFirstUse() {
    // given
    final var creations = new AtomicInteger();

    // when
    final var resource = resource(() -> creations.incrementAndGet());

    // then
    assertThat(creations).hasValue(0);
    assertThat(resource.state()).isEqualTo(State.NOT_CREATED);
    assertThat(resource.getIfCreated()).isEmpty();
  }

  @Test
  void shouldCreateTheResourceOnceForConcurrentFirstUses() throws Exception {
    // given
    final var creations = new AtomicInteger();
    final var factoryEntered = new CountDownLatch(1);
    final var releaseFactory = new CountDownLatch(1);
    final var resource =
        resource(
            () -> {
              factoryEntered.countDown();
              await(releaseFactory);
              return new Object[] {creations.incrementAndGet()};
            });
    final int callers = 16;

    // when
    final List<Future<Object[]>> results = new ArrayList<>();
    try (final var executor = Executors.newFixedThreadPool(callers)) {
      for (int i = 0; i < callers; i++) {
        results.add(executor.submit(resource::get));
      }
      await(factoryEntered);
      releaseFactory.countDown();

      // then
      final var first = results.getFirst().get(10, TimeUnit.SECONDS);
      for (final var result : results) {
        assertThat(result.get(10, TimeUnit.SECONDS)).isSameAs(first);
      }
    }
    assertThat(creations).hasValue(1);
    assertThat(resource.state()).isEqualTo(State.CREATED);
  }

  @Test
  void shouldFailFastWithinTheBackoffAfterAFailedCreation() {
    // given
    final var creations = new AtomicInteger();
    final var resource =
        resource(
            () -> {
              creations.incrementAndGet();
              throw new IllegalStateException("database is down");
            });
    assertThatThrownBy(resource::get)
        .isInstanceOf(PhysicalTenantResourceUnavailableException.class);

    // when
    now.set(Instant.EPOCH.plus(BACKOFF).minusMillis(1));

    // then
    assertThatThrownBy(resource::get)
        .isInstanceOf(PhysicalTenantResourceUnavailableException.class)
        .hasMessageContaining("physical tenant 'tenant-a'")
        .hasMessageContaining("database is down")
        .hasRootCauseInstanceOf(IllegalStateException.class);
    assertThat(creations).hasValue(1);
    assertThat(resource.state()).isEqualTo(State.FAILED);
    assertThat(resource.lastFailure()).hasMessage("database is down");
  }

  @Test
  void shouldRetryTheCreationOnceTheBackoffHasPassed() {
    // given
    final var attempts = new AtomicInteger();
    final var resource =
        resource(
            () -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("database is down");
              }
              return "created";
            });
    assertThatThrownBy(resource::get)
        .isInstanceOf(PhysicalTenantResourceUnavailableException.class);

    // when
    now.set(Instant.EPOCH.plus(BACKOFF));
    final var created = resource.get();

    // then
    assertThat(created).isEqualTo("created");
    assertThat(resource.state()).isEqualTo(State.CREATED);
    assertThat(resource.lastFailure()).isNull();
  }

  @Test
  void shouldCloseTheCreatedResource() {
    // given
    final var resource = resource(() -> "created");
    resource.get();

    // when
    resource.close();

    // then
    assertThat(closed).containsExactly("created");
    assertThat(resource.state()).isEqualTo(State.CLOSED);
    assertThatThrownBy(resource::get).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldNotCreateTheResourceToCloseIt() {
    // given
    final var creations = new AtomicInteger();
    final var resource = resource(() -> creations.incrementAndGet());

    // when
    resource.close();

    // then
    assertThat(creations).hasValue(0);
    assertThat(closed).isEmpty();
    assertThatThrownBy(resource::get).isInstanceOf(IllegalStateException.class);
  }

  private <T> LazyPhysicalTenantResource<T> resource(final Supplier<T> factory) {
    return new LazyPhysicalTenantResource<>(
        "tenant-a", "test resources", factory, closed::add, BACKOFF, now::get);
  }

  private static void await(final CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
