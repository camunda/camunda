/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.backup;

import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;

import java.util.List;

public interface BackupService {
    String SNAPSHOT_MISSING_EXCEPTION_TYPE = "type=snapshot_missing_exception";

    void deleteBackup(Long backupId);

    TakeBackupResponseDto takeBackup(TakeBackupRequestDto request);

    GetBackupStateResponseDto getBackupState(Long backupId);

    List<GetBackupStateResponseDto> getBackups();
}
