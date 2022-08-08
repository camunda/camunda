/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.api;

/** BackupMetadata must uniquely identify a backup stored in the BackupStore. */
public interface BackupIdentifier {

  /**
   * @return Id of the broker which took this backup
   */
  int nodeId();

  /**
   * @return id of the partition of which the backup is taken
   */
  int partitionId();

  /**
   * @return id of the checkpoint included in the backup
   */
  long checkpointId();
}
