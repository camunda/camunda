/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

/**
 * An entry of the authority's maintenance log, {@code maintenance/<seq>.json} (spec section 25.3).
 * The authority is the only writer; restore folds the entries after the commit log of a timeline in
 * mode B.
 *
 * @param seq the maintenance seq, from 1 and gap-free
 * @param basedOnCommitSeq the commit seq whose state the authority saw when it wrote the entry
 * @param object only for {@link Type#COMPACTED}, the object replacing the objects in its range
 * @param upToIndex only for {@link Type#REMOVED}, every object ending at or below it is removed
 * @param upToPosition only for {@link Type#REMOVED}, the last position removed, -1 if none
 */
@JsonPropertyOrder({
  "formatVersion",
  "timelineId",
  "partitionId",
  "seq",
  "type",
  "basedOnCommitSeq",
  "object",
  "upToIndex",
  "upToPosition"
})
public record MaintenanceEntry(
    int formatVersion,
    String timelineId,
    int partitionId,
    long seq,
    Type type,
    long basedOnCommitSeq,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable LogObjectJson object,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long upToIndex,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Long upToPosition) {

  /** The kind of maintenance an entry describes. Serialized by name. */
  public enum Type {
    COMPACTED,
    REMOVED
  }
}
