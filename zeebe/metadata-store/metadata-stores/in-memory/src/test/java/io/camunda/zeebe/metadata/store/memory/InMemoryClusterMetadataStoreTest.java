/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.memory;

import io.camunda.zeebe.metadata.store.testkit.ClusterMetadataStoreContractTest;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.time.InstantSource;
import java.util.concurrent.TimeUnit;

/** The in-memory store against the contract that every implementation has to pass. */
final class InMemoryClusterMetadataStoreTest extends ClusterMetadataStoreContractTest {

  private ActorScheduler scheduler;
  private InMemoryClusterMetadataStore inMemoryStore;
  private boolean closed;

  @Override
  protected StoreUnderTest startStore() {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setIoBoundActorThreadCount(1)
            .build();
    scheduler.start();
    inMemoryStore = new InMemoryClusterMetadataStore("8.11.0", InstantSource.system());
    scheduler.submitActor(inMemoryStore).join(10, TimeUnit.SECONDS);
    return new StoreUnderTest(inMemoryStore, inMemoryStore);
  }

  @Override
  protected void closeStore() {
    if (closed) {
      return;
    }
    closed = true;
    inMemoryStore.closeAsync().join(10, TimeUnit.SECONDS);
    try {
      scheduler.stop().get(10, TimeUnit.SECONDS);
    } catch (final Exception e) {
      throw new IllegalStateException("Failed to stop the actor scheduler", e);
    }
  }
}
