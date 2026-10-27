/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.backup;

import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Narrow port onto history (secondary-storage snapshot) backups, implemented in {@code dist} over
 * the per-physical-tenant {@code BackupServiceRegistry}.
 *
 * <p>The port exists so this module stays storage-agnostic: the real implementation lives in {@code
 * webapps-backup}, whose dependency tree pulls in the Elasticsearch and OpenSearch clients, the
 * Camunda exporter and the webapps schema. Mirrors the relationship {@code RuntimeBackupServices}
 * has with {@code BackupApi}.
 *
 * <p>Implementations are synchronous and block on secondary-storage round-trips; {@link
 * io.camunda.service.HistoryBackupServices} is responsible for moving the call off the request
 * thread. One shared instance serves every physical tenant, which is passed explicitly.
 *
 * <p>Failures are reported as {@code io.camunda.service.exception.ServiceException} so the
 * gateway's error mapper can translate them without a parallel exception hierarchy.
 */
@NullMarked
public interface HistoryBackupApi {

  /** Schedules a backup of the tenant's history and returns the snapshots it scheduled. */
  HistoryBackupTaken takeBackup(String physicalTenantId, long backupId);

  /** Returns the state of a single backup, failing with NOT_FOUND if it does not exist. */
  HistoryBackupState getBackupState(String physicalTenantId, long backupId);

  /**
   * Returns every backup whose id matches {@code prefix}, most recent first by snapshot start time.
   *
   * @param verbose whether to ask the store for snapshot-level detail; when {@code false} the store
   *     reports neither snapshot state nor start time, so the aggregated state and the ordering are
   *     incomplete
   * @param prefix a backup-id prefix ending in {@code *}, or {@code null} for all backups
   */
  List<HistoryBackupState> getBackups(
      String physicalTenantId, boolean verbose, @Nullable String prefix);

  /** Deletes every snapshot making up the given backup. */
  void deleteBackup(String physicalTenantId, long backupId);
}
