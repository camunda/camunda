/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.backup;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchBackupService implements BackupService {
  @Override
  public void deleteBackup(Long backupId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TakeBackupResponseDto takeBackup(TakeBackupRequestDto request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GetBackupStateResponseDto getBackupState(Long backupId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<GetBackupStateResponseDto> getBackups() {
    throw new UnsupportedOperationException();
  }
}
