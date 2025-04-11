/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.management;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

@Component
@RestControllerEndpoint(id = "backups")
@Profile("standalone")
public class BackupService extends ManagementAPIErrorController {

  @Autowired private BackupManager backupManager;

  @Autowired private TasklistProperties tasklistProperties;

  private final Pattern pattern = Pattern.compile("((?![A-Z \"*\\\\<|,>\\/?_]).){0,3996}$");

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
  public TakeBackupResponseDto takeBackup(@RequestBody final TakeBackupRequestDto request) {
    validateRequest(request);
    validateRepositoryNameIsConfigured();
    return backupManager.takeBackup(request);
  }

  private void validateRepositoryNameIsConfigured() {
    if (tasklistProperties.getBackup() == null
        || StringUtils.isBlank(tasklistProperties.getBackup().getRepositoryName())) {
      throw new NotFoundApiException("No backup repository configured.");
    }
  }

  @GetMapping("/{backupId}")
  public GetBackupStateResponseDto getBackupState(@PathVariable final Long backupId) {
    validateBackupId(backupId);
    validateRepositoryNameIsConfigured();
    return backupManager.getBackupState(backupId);
  }

  @GetMapping
  public List<GetBackupStateResponseDto> getBackups(
      @RequestParam(value = "verbose", defaultValue = "true", required = false)
          final boolean verbose,
      @RequestParam(value = "pattern", defaultValue = "*", required = false) final String pattern) {
    validateRepositoryNameIsConfigured();
    return backupManager.getBackups(verbose, pattern);
  }

  @DeleteMapping("/{backupId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteBackup(@PathVariable final Long backupId) {
    validateBackupId(backupId);
    validateRepositoryNameIsConfigured();
    backupManager.deleteBackup(backupId);
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
          "BackupId must be a non-negative Long. Received value: " + backupId);
    }
  }
}
