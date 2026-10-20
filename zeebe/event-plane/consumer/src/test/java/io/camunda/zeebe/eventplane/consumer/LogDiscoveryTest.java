/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.eventplane.store.FileSystemStore;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LogDiscoveryTest {

  @TempDir Path directory;
  private ObjectStore store;

  @BeforeEach
  void setUp() throws IOException {
    store = new FileSystemStore(directory);
    final var history = UUID.randomUUID();
    for (final var log :
        List.of(
            new LogId("c1", "default", 1),
            new LogId("c1", "default", 2),
            new LogId("c1", "other tenant", 1),
            new LogId("c2", "default", 3))) {
      store.put(
          ObjectNames.segmentsPrefix(log.cluster(), log.tenant(), log.partition(), history)
              + "x.seg",
          new byte[1]);
      store.put(ObjectNames.historyPart(log.cluster(), log.tenant(), history, 1), new byte[1]);
    }
    store.put(ObjectNames.worker("g", "w-1"), new byte[1]);
  }

  @Test
  void shouldFindEveryLogOfEveryCluster() throws IOException {
    // when
    final var logs = LogDiscovery.discover(store, List.of(Subscription.all()));

    // then
    assertThat(logs)
        .containsExactly(
            new LogId("c1", "default", 1),
            new LogId("c1", "default", 2),
            new LogId("c1", "other tenant", 1),
            new LogId("c2", "default", 3));
  }

  @Test
  void shouldFindTheLogsOfOneClusterOrTenant() throws IOException {
    // when
    final var ofCluster = LogDiscovery.discover(store, List.of(new Subscription("c1", "*")));
    final var ofTenant =
        LogDiscovery.discover(store, List.of(new Subscription("*", "other tenant")));

    // then
    assertThat(ofCluster)
        .containsExactly(
            new LogId("c1", "default", 1),
            new LogId("c1", "default", 2),
            new LogId("c1", "other tenant", 1));
    assertThat(ofTenant).containsExactly(new LogId("c1", "other tenant", 1));
  }
}
