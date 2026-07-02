/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * Runs a block of state-store writes atomically, so a {@link Task#checkpoint() Task checkpoint} and
 * the {@link OffsetStore} offset write commit as one unit. A {@code
 * RocksDbStateStoreProvider::runInTransaction} method reference satisfies it.
 */
@FunctionalInterface
public interface TransactionRunner {

  void runInTransaction(Runnable operations);
}
