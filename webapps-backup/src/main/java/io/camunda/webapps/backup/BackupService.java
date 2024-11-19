/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import java.util.List;

public interface BackupService {

  void deleteBackup(Long backupId);

  TakeBackupResponseDto takeBackup(TakeBackupRequestDto request);

  GetBackupStateResponseDto getBackupState(Long backupId);

  List<GetBackupStateResponseDto> getBackups();

  record SnapshotRequest(
      String repositoryName, String snapshotName, List<String> indices, Metadata metadata) {}
}
