/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.admin.backup;

import java.util.concurrent.CompletableFuture;

public interface BackupApi {

  /**
   * Triggers backup on all partitions. Returned future is completed successfully after all
   * partitions have processed the request. Returned future fails if the request was not processed
   * by at least one partition.
   *
   * @param backupId the id of the backup to be taken
   * @return the backupId
   */
  CompletableFuture<Long> takeBackup(long backupId);

  /**
   * Returns the status of the backup. The future fails if the request was not processed by at least
   * one partition.
   *
   * @param backupId
   * @return the status of the backup
   */
  CompletableFuture<BackupStatus> getStatus(long backupId);
}
