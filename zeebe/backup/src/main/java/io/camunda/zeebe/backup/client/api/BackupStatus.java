/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.client.api;

import java.util.List;
import java.util.Optional;

/**
 * Shows the aggregated status of the backup and status of backup of each partition.
 *
 * <p>Aggregates status is calculated as follows:
 * <li>COMPLETED => If all partitions have completed backup
 * <li>FAILED => If backup of atleast one partition is failed
 * <li>DOES_NOT_EXIST => If backup of atleast one partition does not exist.
 * <li>IN_PROGRESS => Otherwise
 *
 * @param backupId id of the backup
 * @param status aggregated status of backup
 * @param failureReason If the status == FAILED, then provides a reason for failure
 * @param partitions status of backup of all partitions
 */
public record BackupStatus(
    long backupId,
    State status,
    Optional<String> failureReason,
    List<PartitionBackupStatus> partitions) {}
