/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import java.util.Optional;

public record BackupIdentifierWildcardImpl(
    Optional<Integer> nodeId, Optional<Integer> partitionId, CheckpointPattern checkpointPattern)
    implements BackupIdentifierWildcard {

  @Override
  public boolean matches(final BackupIdentifier id) {
    return (nodeId.isEmpty() || nodeId.get().equals(id.nodeId()))
        && (partitionId.isEmpty() || partitionId.get().equals(id.partitionId()))
        && checkpointPattern.matches(id.checkpointId());
  }
}
