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
 * A checkpoint whose position is covered by the archived log, i.e. {@code checkpointPosition} is at
 * or below the archived position when the entry is written.
 *
 * @param checkpointType the name of the checkpoint type, as in {@code CheckpointType}
 */
@JsonPropertyOrder({"checkpointId", "checkpointPosition", "checkpointType"})
public record CoveredCheckpointJson(
    long checkpointId, long checkpointPosition, String checkpointType) {}
