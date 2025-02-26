/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.filesystem;

public record FilesystemBackupConfig(String basePath) {

  public static class Builder {

    private String basePath;

    /**
     * The base path to store all related backup files in.
     *
     * @param basePath the base path of all backups
     * @return the builder for chaining
     */
    public Builder withBasePath(final String basePath) {
      this.basePath = basePath;
      return this;
    }

    public FilesystemBackupConfig build() {

      return new FilesystemBackupConfig(basePath);
    }
  }
}
