/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.backup.gcs;

public record GcsBackupConfig(String bucketName, String basePath) {
  public GcsBackupConfig {
    if (bucketName == null || bucketName.isEmpty()) {
      throw new IllegalArgumentException("bucketName must be provided");
    }
  }

  public static final class Builder {
    private String bucketName;
    private String basePath;

    public Builder withBucketName(final String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder withBasePath(final String basePath) {
      this.basePath = basePath;
      return this;
    }

    public GcsBackupConfig build() {
      return new GcsBackupConfig(bucketName, basePath);
    }
  }
}
