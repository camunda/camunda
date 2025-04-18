/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.webapps.profiles.ProfileWebAppStandalone;
import io.micrometer.common.lang.NonNull;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@WebEndpoint(id = "backups")
@Conditional(WebappEnabledCondition.class)
@ProfileWebAppStandalone
public class BackupControllerStandalone {
  private final BackupController backupController;

  public BackupControllerStandalone(final BackupController backupController) {
    this.backupController = backupController;
  }

  @WriteOperation
  public WebEndpointResponse<?> takeBackup(final long backupId) {
    return backupController.takeBackup(backupId);
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackupState(@Selector @NonNull final long backupId) {
    return backupController.getBackupState(backupId);
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackups(
      @RequestParam(value = "verbose", defaultValue = "true", required = false)
          final boolean verbose) {
    return backupController.getBackups(verbose);
  }

  @DeleteOperation
  public WebEndpointResponse<?> deleteBackup(@Selector @NonNull final long backupId) {
    return backupController.deleteBackup(backupId);
  }
}
