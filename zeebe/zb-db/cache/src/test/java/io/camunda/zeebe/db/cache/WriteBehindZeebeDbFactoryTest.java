/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import java.io.File;
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
}
