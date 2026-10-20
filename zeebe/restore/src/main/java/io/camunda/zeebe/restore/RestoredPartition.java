/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import java.util.Optional;
import java.util.UUID;

/**
 * Where a restored partition's journal ends: with the entry at {@code checkpointIndex}, which holds
 * the checkpoint record at {@code checkpointPosition}. Up to there, it is the journal of the
 * history the last backup was taken in; backups from before histories existed don't name one.
 */
public record RestoredPartition(
    int partitionId, Optional<UUID> historyId, long checkpointIndex, long checkpointPosition) {}
