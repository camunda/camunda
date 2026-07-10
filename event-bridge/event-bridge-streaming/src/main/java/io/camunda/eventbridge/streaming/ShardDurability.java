/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

/**
 * The durable half of one partition shard, injected into a {@link StreamProcessor} at construction:
 * the shard's transaction scope plus the consumed source offset stored inside the same backend. It
 * is what makes the processor a self-contained shard — it restores its own resume position and
 * commits every stage's state and the offset as one atomic cut, with nothing owned by the runtime.
 *
 * <p>One instance per partition, driven only by that partition's task — implementations need no
 * synchronization. A typical implementation delegates {@link #runInTransaction} to the shard's
 * {@link TransactionRunner} and keeps the offset in a small column family of the same store.
 */
public interface ShardDurability {

  /** Runs a block of state writes atomically in the shard's own transaction. */
  void runInTransaction(Runnable operations);

  /**
   * The last committed source offset of this shard, or {@link Task#NO_OFFSET} when nothing was ever
   * committed — in which case the runtime rebuilds the shard from the source start.
   */
  long readOffset();

  /**
   * Records the consumed source offset. Called inside {@link #runInTransaction}, so the offset
   * lands atomically with the state it covers.
   */
  void persistOffset(long offset);
}
