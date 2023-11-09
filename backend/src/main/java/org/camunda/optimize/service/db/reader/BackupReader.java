/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.elasticsearch.snapshots.SnapshotInfo;

import java.util.List;
import java.util.Map;

public interface BackupReader {

  void validateRepositoryExistsOrFail();

  void validateNoDuplicateBackupId(final Long backupId);

  Map<Long, List<SnapshotInfo>> getAllOptimizeSnapshotsByBackupId();

  List<SnapshotInfo> getAllOptimizeSnapshots();

  List<SnapshotInfo> getOptimizeSnapshotsForBackupId(final Long backupId);

}
