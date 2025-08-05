/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.gcs;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException;
import io.camunda.zeebe.backup.gcs.GcsConnectionConfig.Authentication.Auto;
import io.camunda.zeebe.backup.gcs.GcsConnectionConfig.Authentication.None;

public record GcsBackupConfig(String bucketName, String basePath, GcsConnectionConfig connection) {
  public GcsBackupConfig(
      final String bucketName, final String basePath, final GcsConnectionConfig connection) {
    this.bucketName = requireBucketName(bucketName);
    this.basePath = sanitizeBasePath(basePath);
    this.connection = requireNonNull(connection);
  }

  private static String requireBucketName(final String bucketName) {
    if (bucketName == null || bucketName.isBlank()) {
      throw new ConfigurationException("bucketName must be provided");
    }
    return bucketName;
  }

  private static String sanitizeBasePath(final String basePath) {
    if (basePath == null) {
      return null;
    }

    var sanitized = basePath.trim();
    if (sanitized.isEmpty() || sanitized.equals("/")) {
      return null;
    }

    // Remove one leading and one trailing slash if present.
    while (sanitized.startsWith("/")) {
      sanitized = sanitized.substring(1);
    }
    while (sanitized.endsWith("/")) {
      sanitized = sanitized.substring(0, sanitized.length() - 1);
    }

    if (sanitized.isBlank()) {
      throw new ConfigurationException(
          "After removing leading and trailing '/' characters from basePath '%s', the remainder is empty and not a valid base path"
              .formatted(basePath));
    }
    return sanitized;
  }

  public static final class Builder {
    private String bucketName;
    private String basePath;
    private String host;
    private GcsConnectionConfig.Authentication auth;

    public Builder withBucketName(final String bucketName) {
      this.bucketName = bucketName;
      return this;
    }

    public Builder withBasePath(final String basePath) {
      this.basePath = basePath;
      return this;
    }

    public Builder withHost(final String host) {
      this.host = host;
      return this;
    }

    public Builder withoutAuthentication() {
      // This is only used for testing, so we can make up a project id for it.
      auth = new None("tmp");
      return this;
    }

    public Builder withAutoAuthentication() {
      auth = new Auto();
      return this;
    }

    public GcsBackupConfig build() {
      return new GcsBackupConfig(bucketName, basePath, new GcsConnectionConfig(host, auth));
    }
  }
}
