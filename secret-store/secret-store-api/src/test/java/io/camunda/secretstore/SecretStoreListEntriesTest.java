/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;

class SecretStoreListEntriesTest {

  private static final List<SecretListEntry> STATES =
      List.of(
          new SecretListEntry("active", SecretStatus.ACTIVE),
          new SecretListEntry("disabled", SecretStatus.DISABLED),
          new SecretListEntry("expired", SecretStatus.EXPIRED),
          new SecretListEntry("later", SecretStatus.NOT_YET_ACTIVE));

  @Test
  void shouldReportEveryListedNameAsActiveForAStoreWithoutStates() {
    // given a store that only implements list()
    final SecretStore store = new StoreWithoutStates("a", "b");

    // when
    final var entries = store.listEntries();

    // then
    assertThat(entries)
        .containsExactly(
            new SecretListEntry("a", SecretStatus.ACTIVE),
            new SecretListEntry("b", SecretStatus.ACTIVE));
  }

  @Test
  void shouldListNothingForTheNoopStore() {
    // when / then
    assertThat(new NoopSecretStore().listEntries()).isEmpty();
  }

  @Test
  void shouldKeepTheStatesOfAStoreWrappedByTheRegistry() {
    // given a store reporting states, wrapped for caching the way the registry wraps every store
    final var registry =
        new SecretStoreRegistry(
            Map.of("default", new StoreWithStates()), Map.of("default", new InMemorySecretCache()));

    // when
    final var entries = registry.getStores().get("default").listEntries();

    // then a wrapper that forgot to delegate would report all of them as ACTIVE
    assertThat(entries).containsExactlyElementsOf(STATES);
  }

  @Test
  void shouldKeepTheStatesOfAStoreWrappedForConcurrency() {
    // given a cached store behind the concurrent wrapper, as the application stacks them
    final var cached = new CachingSecretStore(new StoreWithStates(), new InMemorySecretCache());
    try (final var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      final var store = new ConcurrentSecretStore(cached, pool, new Semaphore(4, true));

      // when
      final var entries = store.listEntries();

      // then
      assertThat(entries).containsExactlyElementsOf(STATES);
    }
  }

  private static final class StoreWithoutStates implements SecretStore {
    private final List<String> names;

    StoreWithoutStates(final String... names) {
      this.names = List.of(names);
    }

    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> requested) {
      return Map.of();
    }

    @Override
    public List<String> list() {
      return names;
    }
  }

  /** A store reporting the given entries, whose {@code list()} names exactly those secrets. */
  private static final class StoreWithStates implements SecretStore {
    @Override
    public Map<String, SecretResolutionResult> resolve(final Set<String> requested) {
      return Map.of();
    }

    @Override
    public List<String> list() {
      return STATES.stream().map(SecretListEntry::name).toList();
    }

    @Override
    public List<SecretListEntry> listEntries() {
      return STATES;
    }
  }
}
