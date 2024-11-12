/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.Optional;

/**
 * Similar to {@link BackupIdentifier} but fields that are omitted should be interpreted as a
 * wildcard.
 */
public interface BackupIdentifierWildcard {
  /**
   * @return id of the broker which took this backup.
   */
  Optional<Integer> nodeId();

  /**
   * @return id of the partition of which the backup is taken
   */
  Optional<Integer> partitionId();

  /**
   * @return id of the checkpoint included in the backup
   */
  Optional<Long> checkpointId();

  /**
   * Predicate that tries to match an id to this wildcard.
   *
   * @return true if the given id matches the wildcard, otherwise false.
   */
  boolean matches(BackupIdentifier id);
}
