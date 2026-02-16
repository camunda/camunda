/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.util.Objects;
import java.util.zip.Deflater;

public class GcsBackupStoreConfig implements ConfigurationEntry {
  private String bucketName;
  private String basePath;
  private String host;
  private GcsBackupStoreAuth auth = GcsBackupStoreAuth.AUTO;

  /** Default maximum concurrent transfers (uploads and downloads) */
  private int maxConcurrentTransfers = 8;

  /** Default compression level for gzip compression (Deflater.DEFAULT_COMPRESSION = -1) */
  private int compressionLevel = Deflater.DEFAULT_COMPRESSION;

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public GcsBackupStoreAuth getAuth() {
    return auth;
  }

  public void setAuth(final GcsBackupStoreAuth auth) {
    this.auth = auth;
  }

  public int getMaxConcurrentTransfers() {
    return maxConcurrentTransfers;
  }

  public void setMaxConcurrentTransfers(final int maxConcurrentTransfers) {
    this.maxConcurrentTransfers = maxConcurrentTransfers;
  }

  public int getCompressionLevel() {
    return compressionLevel;
  }

  public void setCompressionLevel(final int compressionLevel) {
    this.compressionLevel = compressionLevel;
  }

  public static GcsBackupConfig toStoreConfig(final GcsBackupStoreConfig config) {
    final var storeConfig =
        new GcsBackupConfig.Builder()
            .withBucketName(config.getBucketName())
            .withBasePath(config.getBasePath())
            .withHost(config.getHost())
            .withMaxConcurrentTransfers(config.getMaxConcurrentTransfers())
            .withCompressionLevel(config.getCompressionLevel());
    final var authenticated =
        switch (config.getAuth()) {
          case NONE -> storeConfig.withoutAuthentication();
          case AUTO -> storeConfig.withAutoAuthentication();
        };
    return authenticated.build();
  }

  @Override
  public int hashCode() {
    return Objects.hash(bucketName, basePath, host, auth, maxConcurrentTransfers, compressionLevel);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final GcsBackupStoreConfig that = (GcsBackupStoreConfig) o;
    return Objects.equals(bucketName, that.bucketName)
        && Objects.equals(basePath, that.basePath)
        && Objects.equals(host, that.host)
        && auth == that.auth
        && maxConcurrentTransfers == that.maxConcurrentTransfers
        && compressionLevel == that.compressionLevel;
  }

  @Override
  public String toString() {
    return "GcsBackupStoreConfig{"
        + "bucketName='"
        + bucketName
        + '\''
        + ", basePath='"
        + basePath
        + '\''
        + ", host='"
        + host
        + '\''
        + ", auth="
        + auth
        + ", maxConcurrentTransfers="
        + maxConcurrentTransfers
        + ", compressionLevel="
        + compressionLevel
        + '}';
  }

  public enum GcsBackupStoreAuth {
    NONE,
    AUTO
  }
}
