/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.reader.BackupReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class BackupReaderOS implements BackupReader {

  @Override
  public void validateRepositoryExistsOrFail() {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public void validateNoDuplicateBackupId(final Long backupId) {
    log.debug("Functionality not implemented for OpenSearch");
  }

  @Override
  public Map<Long, List<SnapshotInfo>> getAllOptimizeSnapshotsByBackupId() {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<SnapshotInfo> getAllOptimizeSnapshots() {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }

  @Override
  public List<SnapshotInfo> getOptimizeSnapshotsForBackupId(final Long backupId) {
    log.debug("Functionality not implemented for OpenSearch");
    return new ArrayList<>();
  }
}
