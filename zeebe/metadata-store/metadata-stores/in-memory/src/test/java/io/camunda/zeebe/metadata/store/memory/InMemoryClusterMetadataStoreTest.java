/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metadata.store.memory;

import io.camunda.zeebe.metadata.store.testkit.ClusterMetadataStoreContractTest;

/** The in-memory store against the contract that every implementation has to pass. */
final class InMemoryClusterMetadataStoreTest extends ClusterMetadataStoreContractTest {

  private InMemoryClusterMetadataStore store;

  @Override
  protected StoreUnderTest startStore() {
    store = new InMemoryClusterMetadataStore();
    return new StoreUnderTest(store, store);
  }

  @Override
  protected void closeStore() {
    store.close();
  }
}
