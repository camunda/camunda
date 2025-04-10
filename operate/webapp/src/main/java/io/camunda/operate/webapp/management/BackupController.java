/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.management;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.api.v1.rest.ErrorController;
import io.camunda.operate.webapp.backup.BackupService;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

@Component
@RestControllerEndpoint(id = "backups")
@Profile("standalone")
public class BackupController extends ErrorController {

  @Autowired private BackupService backupService;
  @Autowired private OperateProperties operateProperties;

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public TakeBackupResponseDto takeBackup(@RequestBody final TakeBackupRequestDto request) {
    validateRequest(request);
    validateRepositoryNameIsConfigured();
    return backupService.takeBackup(request);
  }

  private void validateRepositoryNameIsConfigured() {
    if (operateProperties.getBackup() == null
        || operateProperties.getBackup().getRepositoryName() == null
        || operateProperties.getBackup().getRepositoryName().isEmpty()) {
      throw new NotFoundException("No backup repository configured.");
    }
  }

  @GetMapping("/{backupId}")
  public GetBackupStateResponseDto getBackupState(@PathVariable final Long backupId) {
    validateBackupId(backupId);
    validateRepositoryNameIsConfigured();
    return backupService.getBackupState(backupId);
  }

  @GetMapping
  public List<GetBackupStateResponseDto> getBackups(
      @RequestParam(value = "verbose", defaultValue = "true", required = false)
          final boolean verbose) {
    validateRepositoryNameIsConfigured();
    return backupService.getBackups(verbose);
  }

  @DeleteMapping("/{backupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBackup(@PathVariable final Long backupId) {
    validateBackupId(backupId);
    validateRepositoryNameIsConfigured();
    backupService.deleteBackup(backupId);
  }

  private void validateRequest(final TakeBackupRequestDto request) {
    if (request.getBackupId() == null) {
      throw new InvalidRequestException("BackupId must be provided");
    }
    validateBackupId(request.getBackupId());
  }

  private void validateBackupId(final Long backupId) {
    if (backupId < 0) {
      throw new InvalidRequestException(
          "BackupId must be a non-negative Integer. Received value: " + backupId);
    }
  }
}
