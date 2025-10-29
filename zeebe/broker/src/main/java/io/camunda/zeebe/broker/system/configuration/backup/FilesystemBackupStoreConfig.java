/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.util.Objects;

public class FilesystemBackupStoreConfig implements ConfigurationEntry {

  private String basePath;

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public static FilesystemBackupConfig toStoreConfig(final FilesystemBackupStoreConfig config) {
    return new FilesystemBackupConfig.Builder().withBasePath(config.getBasePath()).build();
  }

  @Override
  public int hashCode() {
    return Objects.hash(basePath);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FilesystemBackupStoreConfig that = (FilesystemBackupStoreConfig) o;
    return Objects.equals(basePath, that.basePath);
  }

  @Override
  public String toString() {
    return "FilesystemBackupStoreConfig{" + "basePath='" + basePath + '\'' + '}';
  }
}
