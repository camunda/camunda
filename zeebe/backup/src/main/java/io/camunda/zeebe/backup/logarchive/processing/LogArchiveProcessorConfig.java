/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.processing;

/**
 * Settings of the {@link LogArchiveRecordsProcessor} that influence replicated state or the
 * requests it hands to the leader components. Must be identical on all replicas.
 *
 * @param partitionId the partition the processor runs on, written into all bucket objects
 * @param chunkSize number of open objects after which an index chunk is sealed
 * @param rootEvery a root is attached to every commit entry whose seq is a multiple of this
 */
public record LogArchiveProcessorConfig(int partitionId, int chunkSize, int rootEvery) {

  public LogArchiveProcessorConfig {
    if (chunkSize < 1) {
      throw new IllegalArgumentException("chunkSize must be positive, but was " + chunkSize);
    }
    if (rootEvery < 1) {
      throw new IllegalArgumentException("rootEvery must be positive, but was " + rootEvery);
    }
  }
}
