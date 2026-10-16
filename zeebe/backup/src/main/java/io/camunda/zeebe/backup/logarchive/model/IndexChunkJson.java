/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A sealed index chunk as it is referenced from commit entries and roots. The chunk file itself is
 * {@link IndexChunkFile}.
 *
 * @param lastCheckpointId {@code -1} if the chunk covers no checkpoint
 * @param firstCheckpointId {@code -1} if the chunk covers no checkpoint
 * @param sha256 lowercase hex SHA-256 of the chunk file
 */
@JsonPropertyOrder({
  "chunkNo",
  "key",
  "firstIndex",
  "lastIndex",
  "firstPosition",
  "lastPosition",
  "objectCount",
  "firstCheckpointId",
  "lastCheckpointId",
  "sha256"
})
public record IndexChunkJson(
    long chunkNo,
    String key,
    long firstIndex,
    long lastIndex,
    long firstPosition,
    long lastPosition,
    int objectCount,
    long firstCheckpointId,
    long lastCheckpointId,
    String sha256) {}
