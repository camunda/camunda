/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions;

import io.zeebe.snapshots.SnapshotChunk;
import java.util.function.Consumer;

public interface SnapshotReplication extends AutoCloseable {

  /**
   * Replicates the given snapshot chunk.
   *
   * @param snapshot the chunk to replicate
   */
  void replicate(SnapshotChunk snapshot);

  /**
   * Registers an consumer, which should be called when an snapshot chunk was received.
   *
   * @param consumer the consumer which should be called
   */
  void consume(Consumer<SnapshotChunk> consumer);
}
