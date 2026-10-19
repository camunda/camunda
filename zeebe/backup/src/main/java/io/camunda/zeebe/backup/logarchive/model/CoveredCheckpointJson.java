/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * A checkpoint whose position is covered by the archived log, i.e. {@code checkpointPosition} is at
 * or below the archived position when the entry is written.
 *
 * @param checkpointType the name of the checkpoint type, as in {@code CheckpointType}
 * @param checkpointTimestamp the timestamp of the checkpoint in epoch millis as recorded when it
 *     was created, -1 if unknown (objects written before the timestamp was recorded lack the field)
 */
@JsonPropertyOrder({"checkpointId", "checkpointPosition", "checkpointType", "checkpointTimestamp"})
public record CoveredCheckpointJson(
    long checkpointId, long checkpointPosition, String checkpointType, long checkpointTimestamp) {

  public CoveredCheckpointJson(
      final long checkpointId, final long checkpointPosition, final String checkpointType) {
    this(checkpointId, checkpointPosition, checkpointType, -1L);
  }

  /** Reads a missing timestamp as -1, which a record's canonical constructor cannot express. */
  @JsonCreator
  static CoveredCheckpointJson fromJson(
      @JsonProperty("checkpointId") final long checkpointId,
      @JsonProperty("checkpointPosition") final long checkpointPosition,
      @JsonProperty("checkpointType") final String checkpointType,
      @JsonProperty("checkpointTimestamp") final @Nullable Long checkpointTimestamp) {
    return new CoveredCheckpointJson(
        checkpointId,
        checkpointPosition,
        checkpointType,
        checkpointTimestamp == null ? -1L : checkpointTimestamp);
  }
}
