/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.atomix.cluster.BrokerMemberId;
import org.jspecify.annotations.Nullable;

/** Uniquely identifies a backup stored in the BackupStore. */
public interface BackupIdentifier {

  /**
   * @return id of the broker which took this backup
   */
  int nodeId();

  /**
   * @return zone of the broker which took this backup, or {@code null} when the cluster is not
   *     zone-aware
   */
  @Nullable String zone();

  /**
   * @return id of the partition of which the backup is taken
   */
  int partitionId();

  /**
   * @return id of the checkpoint included in the backup
   */
  long checkpointId();

  /**
   * @return the broker member identifier combining zone and nodeId, for use in backup storage paths
   */
  default BrokerMemberId brokerId() {
    return BrokerMemberId.from(zone(), nodeId());
  }
}
