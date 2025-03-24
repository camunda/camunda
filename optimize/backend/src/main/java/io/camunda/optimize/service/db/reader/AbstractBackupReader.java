/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public abstract class AbstractBackupReader implements BackupReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AbstractBackupReader.class);

  @Override
  public void validateRepositoryExists() {
    if (StringUtils.isEmpty(getSnapshotRepositoryName())) {
      final String reason =
          "Cannot execute backup request because no snapshot repository name found in Optimize configuration.";
      LOG.error(reason);
      throw new OptimizeConfigurationException(reason);
    } else {
      validateRepositoryExistsOrFail();
    }
  }

  protected abstract String getSnapshotRepositoryName();

  protected abstract void validateRepositoryExistsOrFail();
}
