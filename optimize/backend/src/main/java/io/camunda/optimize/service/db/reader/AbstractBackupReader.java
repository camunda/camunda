/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public abstract class AbstractBackupReader implements BackupReader {
  @Override
  public void validateRepositoryExists() {
    if (StringUtils.isEmpty(getSnapshotRepositoryName())) {
      final String reason =
          "Cannot execute backup request because no snapshot repository name found in Optimize configuration.";
      log.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      validateRepositoryExistsOrFail();
    }
  }

  protected abstract String getSnapshotRepositoryName();

  protected abstract void validateRepositoryExistsOrFail();
}
