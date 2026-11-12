/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class WriteBehindZeebeDbFactoryTest {

  @Test
  @SuppressWarnings("unchecked")
  void shouldCloseTheStorageIfTheCacheFailsToStart() throws Exception {
    // given - a storage the cache can't register its meters on
    final ZeebeDb<TestColumnFamily> storageDb = mock(ZeebeDb.class);
    when(storageDb.getMeterRegistry()).thenThrow(new IllegalStateException("expected"));
    final ZeebeDbFactory<TestColumnFamily> storage = mock(ZeebeDbFactory.class);
    final var directory = new File("runtime");
    when(storage.createDb(directory)).thenReturn(storageDb);
    final var factory =
        new WriteBehindZeebeDbFactory<>(
            storage, WriteBehindConfiguration.DEFAULT, new ConsistencyChecksSettings(true, true));

    // when - then
    assertThatThrownBy(() -> factory.createDb(directory)).hasMessage("expected");
    verify(storageDb).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldGiveEachDatabaseItsOwnCommitGate() throws Exception {
    // given - the broker opens a database for each leader term, and each term has its own gate
    final var firstStorage = new InMemoryStorage();
    final var secondStorage = new InMemoryStorage();
    final ZeebeDbFactory<TestColumnFamily> storage = mock(ZeebeDbFactory.class);
    when(storage.createDb(new File("first"))).thenReturn(firstStorage.db());
    when(storage.createDb(new File("second"))).thenReturn(secondStorage.db());
    final List<TestCommitGate> gates = new ArrayList<>();
    final var factory =
        new WriteBehindZeebeDbFactory<>(
            storage,
            WriteBehindConfiguration.DEFAULT,
            () -> {
              final var gate = new TestCommitGate();
              gates.add(gate);
              return gate;
            },
            new ConsistencyChecksSettings(true, true));

    // when
    try (final var first = factory.createDb(new File("first"));
        final var second = factory.createDb(new File("second"))) {

      // then
      assertThat(gates).hasSize(2).allSatisfy(gate -> assertThat(gate.listeners()).isNotEmpty());
    }
  }
}
