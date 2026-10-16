/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * The content of {@code index/<firstIndex>-<lastIndex>.json} (spec section 9.3). Both lists are
 * always written, even when empty.
 *
 * @param objects the objects of the chunk, in index order
 * @param checkpoints the checkpoints covered by the chunk's position range
 */
@JsonPropertyOrder({
  "formatVersion",
  "timelineId",
  "partitionId",
  "chunkNo",
  "objects",
  "checkpoints"
})
public record IndexChunkFile(
    int formatVersion,
    String timelineId,
    int partitionId,
    long chunkNo,
    List<LogObjectJson> objects,
    List<CoveredCheckpointJson> checkpoints) {

  public IndexChunkFile {
    objects = objects == null ? List.of() : List.copyOf(objects);
    checkpoints = checkpoints == null ? List.of() : List.copyOf(checkpoints);
  }
}
