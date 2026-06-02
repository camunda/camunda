/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.service.secret.SecretStore;
import io.camunda.service.secret.SimpleSecretStoreRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SecretServicesTest {

  private static SecretServices serviceWith(final Map<String, String> data) {
    final var store =
        new SecretStore() {
          @Override
          public String id() {
            return "test";
          }

          @Override
          public Optional<String> resolve(final String secretName) {
            return Optional.ofNullable(data.get(secretName));
          }
        };
    return new SecretServices(new SimpleSecretStoreRegistry(store), Duration.ofSeconds(20));
  }

  @Test
  void shouldResolveKnownReferences() {
    final var service = serviceWith(Map.of("FOO", "foo-value", "BAR", "bar-value"));

    final var result = service.resolve(List.of("camunda.secrets.FOO", "camunda.secrets.BAR"));

    assertThat(result)
        .containsEntry("camunda.secrets.FOO", "foo-value")
        .containsEntry("camunda.secrets.BAR", "bar-value");
  }

  @Test
  void shouldOmitMissingReferences() {
    final var service = serviceWith(Map.of("FOO", "foo-value"));

    final var result = service.resolve(List.of("camunda.secrets.FOO", "camunda.secrets.MISSING"));

    assertThat(result)
        .containsOnlyKeys("camunda.secrets.FOO")
        .containsEntry("camunda.secrets.FOO", "foo-value");
  }

  @Test
  void shouldOmitMalformedReferences() {
    final var service = serviceWith(Map.of("FOO", "foo-value"));

    final var result =
        service.resolve(
            List.of("FOO", "camunda.secret.FOO", "camunda.secrets.", "camunda.secrets.FOO"));

    assertThat(result).containsOnlyKeys("camunda.secrets.FOO");
  }

  @Test
  void shouldReturnEmptyForEmptyOrNullInput() {
    final var service = serviceWith(Map.of());

    assertThat(service.resolve(List.of())).isEmpty();
    assertThat(service.resolve(null)).isEmpty();
  }

  @Test
  void shouldCacheResolvedValuesAcrossCalls() {
    final var callCount = new AtomicInteger();
    final var store =
        spy(
            new SecretStore() {
              @Override
              public String id() {
                return "test";
              }

              @Override
              public Optional<String> resolve(final String secretName) {
                callCount.incrementAndGet();
                return "FOO".equals(secretName) ? Optional.of("foo-value") : Optional.empty();
              }
            });
    final var service =
        new SecretServices(new SimpleSecretStoreRegistry(store), Duration.ofSeconds(20));

    service.resolve(List.of("camunda.secrets.FOO"));
    service.resolve(List.of("camunda.secrets.FOO"));
    service.resolve(List.of("camunda.secrets.FOO"));

    verify(store, times(1)).resolve("FOO");
  }

  @Test
  void shouldNotCacheMissingReferences() {
    // Misses should not be cached, so a later successful resolution after env change still works
    final var calls = new AtomicInteger();
    final var store =
        new SecretStore() {
          @Override
          public String id() {
            return "test";
          }

          @Override
          public Optional<String> resolve(final String secretName) {
            // First call: missing; subsequent calls: present
            return calls.getAndIncrement() == 0 ? Optional.empty() : Optional.of("late-value");
          }
        };
    final var service =
        new SecretServices(new SimpleSecretStoreRegistry(store), Duration.ofSeconds(20));

    assertThat(service.resolve(List.of("camunda.secrets.FOO"))).isEmpty();
    assertThat(service.resolve(List.of("camunda.secrets.FOO")))
        .containsEntry("camunda.secrets.FOO", "late-value");
  }

  @Test
  void shouldNotCallStoreForMalformedReferences() {
    final var store =
        spy(
            new SecretStore() {
              @Override
              public String id() {
                return "test";
              }

              @Override
              public Optional<String> resolve(final String secretName) {
                return Optional.empty();
              }
            });
    final var service =
        new SecretServices(new SimpleSecretStoreRegistry(store), Duration.ofSeconds(20));

    service.resolve(List.of("not-a-ref", "camunda.foo.BAR"));

    verify(store, never()).resolve(org.mockito.ArgumentMatchers.anyString());
  }
}
