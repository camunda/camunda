/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import java.util.List;
import java.util.Map;
import org.elasticsearch.snapshots.SnapshotInfo;

public interface BackupReader {

  void validateRepositoryExistsOrFail();

  void validateNoDuplicateBackupId(final Long backupId);

  Map<Long, List<SnapshotInfo>> getAllOptimizeSnapshotsByBackupId();

  List<SnapshotInfo> getAllOptimizeSnapshots();

  List<SnapshotInfo> getOptimizeSnapshotsForBackupId(final Long backupId);
}
