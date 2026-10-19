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
 * The content of {@code metadata/<partition>/handover/<H>.json} in the store of the old backups
 * (spec section 18.2). It is a pointer from the old backups to the log archive and never changes
 * anything that is already there: the same handover always yields the same bytes.
 *
 * @param checkpointId the id of the handover checkpoint H
 * @param checkpointPosition the log position of H
 * @param logArchiveTimeline the log archive timeline the handover was recorded on
 */
@JsonPropertyOrder({
  "formatVersion",
  "partitionId",
  "checkpointId",
  "checkpointPosition",
  "logArchiveTimeline"
})
public record HandoverFile(
    int formatVersion,
    int partitionId,
    long checkpointId,
    long checkpointPosition,
    String logArchiveTimeline) {}
