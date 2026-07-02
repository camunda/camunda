/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

/**
 * Runs a block of state-store writes atomically. Lets the durable rollup co-commit its changed
 * cells and the consumed offsets in one transaction without depending on the concrete state-store
 * provider. A {@code RocksDbStateStoreProvider::runInTransaction} method reference satisfies it.
 */
@FunctionalInterface
public interface TransactionRunner {

  void runInTransaction(Runnable operations);
}
