/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.management;

import io.camunda.operate.webapp.api.v1.rest.ErrorController;
import io.camunda.operate.webapp.es.backup.BackupManager;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.regex.Pattern;

@Component
@RestControllerEndpoint(id = "backup")
public class BackupService {

  @Autowired
  private BackupManager backupManager;

  private final Pattern pattern = Pattern.compile("((?![A-Z \"*\\\\<|,>\\/?]).){0,3996}$");

  @PostMapping(produces = { MediaType.APPLICATION_JSON_VALUE})
  public TakeBackupResponseDto takeBackup(@RequestBody TakeBackupRequestDto request) {
    validateRequest(request);
    return backupManager.takeBackup(request);
  }

  @GetMapping("/{backupId}")
  public GetBackupStateResponseDto getBackupState(@PathVariable String backupId) {
    return backupManager.getBackupState(backupId);
  }

  private void validateRequest(TakeBackupRequestDto request) {
    if (request.getBackupId() == null) {
      throw new InvalidRequestException("BackupId must be provided");
    }
    if (!pattern.matcher(request.getBackupId()).matches()) {
      throw new InvalidRequestException(
          "BackupId must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >, /, ?].");
    }
  }

}
