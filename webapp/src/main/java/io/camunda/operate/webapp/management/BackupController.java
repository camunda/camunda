/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.api.v1.rest.ErrorController;
import io.camunda.operate.webapp.es.backup.BackupManager;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RestControllerEndpoint(id = "backups")
public class BackupController extends ErrorController {

  @Autowired
  private BackupManager backupManager;

  @Autowired
  private OperateProperties operateProperties;

  private final Pattern pattern = Pattern.compile("((?![A-Z \"*\\\\<|,>\\/?_]).){0,3996}$");

  @PostMapping(produces = { MediaType.APPLICATION_JSON_VALUE})
  public TakeBackupResponseDto takeBackup(@RequestBody TakeBackupRequestDto request) {
    validateRequest(request);
    validateRepositoryNameIsConfigured();
    return backupManager.takeBackup(request);
  }

  private void validateRepositoryNameIsConfigured() {
    if (operateProperties.getBackup() == null || operateProperties.getBackup()
        .getRepositoryName() == null || operateProperties.getBackup().getRepositoryName().isEmpty()) {
      throw new NotFoundException("No backup repository configured.");
    }
  }

  @GetMapping("/{backupId}")
  public GetBackupStateResponseDto getBackupState(@PathVariable Integer backupId) {
    validateBackupId(backupId);
    validateRepositoryNameIsConfigured();
    return backupManager.getBackupState(backupId);
  }

  @GetMapping
  public List<GetBackupStateResponseDto> getBackups() {
    validateRepositoryNameIsConfigured();
    return backupManager.getBackups();
  }

  @DeleteMapping("/{backupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBackup(@PathVariable Integer backupId) {
    validateBackupId(backupId);
    validateRepositoryNameIsConfigured();
    backupManager.deleteBackup(backupId);
  }

  private void validateRequest(TakeBackupRequestDto request) {
    if (request.getBackupId() == null) {
      throw new InvalidRequestException("BackupId must be provided");
    }
    validateBackupId(request.getBackupId());
  }

  private void validateBackupId(Integer backupId) {
    if (backupId < 0) {
      throw new InvalidRequestException(
          "BackupId must be a non-negative Integer. Received value: " + backupId);
    }
  }

}
