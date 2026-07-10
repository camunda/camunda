/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * Runs a block of state-store writes atomically. Owned by a task's own state backend — a {@code
 * RocksDbStateStoreProvider::runInTransaction} method reference satisfies it — and used wherever a
 * shard makes its commit cut durable: the aggregation operators persist their checkpoint writes
 * through it, and a {@link ShardDurability} wraps one so state and the consumed offset land as one
 * atomic cut.
 */
@FunctionalInterface
public interface TransactionRunner {

  void runInTransaction(Runnable operations);
}
