/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.LinkedHashSet;
import java.util.Set;

public class Gcs {
  private static final String PREFIX = "camunda.data.primary-storage.backup.gcs";
  private static final Set<Set<String>> LEGACY_BUCKETNAME_PROPERTIES = new LinkedHashSet<>(2);
  private static final Set<Set<String>> LEGACY_BASEPATH_PROPERTIES = new LinkedHashSet<>(2);
  private static final Set<Set<String>> LEGACY_HOST_PROPERTIES = new LinkedHashSet<>(2);
  private static final Set<Set<String>> LEGACY_AUTH_PROPERTIES = new LinkedHashSet<>(2);

  static {
    LEGACY_BUCKETNAME_PROPERTIES.add(Set.of("zeebe.broker.data.backup.gcs.bucketName"));
    LEGACY_BUCKETNAME_PROPERTIES.add(Set.of("camunda.data.backup.gcs.bucket-name"));

    LEGACY_BASEPATH_PROPERTIES.add(Set.of("zeebe.broker.data.backup.gcs.basePath"));
    LEGACY_BASEPATH_PROPERTIES.add(Set.of("camunda.data.backup.gcs.base-path"));

    LEGACY_HOST_PROPERTIES.add(Set.of("zeebe.broker.data.backup.gcs.host"));
    LEGACY_HOST_PROPERTIES.add(Set.of("camunda.data.backup.gcs.host"));

    LEGACY_AUTH_PROPERTIES.add(Set.of("zeebe.broker.data.backup.gcs.auth"));
    LEGACY_AUTH_PROPERTIES.add(Set.of("camunda.data.backup.gcs.auth"));
  }

  /**
   * Name of the bucket where the backup will be stored. The bucket must already exist. The bucket
   * must not be shared with other Zeebe clusters unless basePath is also set.
   */
  private String bucketName;

  /**
   * When set, all blobs in the bucket will use this prefix. Useful for using the same bucket for
   * multiple Zeebe clusters. In this case, basePath must be unique. Should not start or end with
   * '/' character. Must be non-empty and not consist of only '/' characters.
   */
  private String basePath;

  /**
   * When set, this overrides the host that the GCS client connects to. # By default, this is not
   * set because the client can automatically discover the correct host to # connect to.
   */
  private String host;

  /**
   * Configures which authentication method is used for connecting to GCS. Can be either 'auto' or
   * 'none'. Choosing 'auto' means that the GCS client uses application default credentials which
   * automatically discovers appropriate credentials from the runtime environment: <a
   * href="https://cloud.google.com/docs/authentication/application-default-credentials">...</a>
   * Choosing 'none' means that no authentication is attempted which is only applicable for testing
   * with emulated GCS.
   */
  private GcsBackupStoreAuth auth = GcsBackupStoreAuth.AUTO;

  /** Maximum number of concurrent file transfers (uploads and downloads) using virtual threads. */
  private int maxConcurrentTransfers = 8;

  public String getBucketName() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".bucket-name",
        bucketName,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BUCKETNAME_PROPERTIES);
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }

  public String getBasePath() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".base-path",
        basePath,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_BASEPATH_PROPERTIES);
  }

  public void setBasePath(final String basePath) {
    this.basePath = basePath;
  }

  public String getHost() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".host",
        host,
        String.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_HOST_PROPERTIES);
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public GcsBackupStoreAuth getAuth() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationWithOrdering(
        PREFIX + ".auth",
        auth,
        GcsBackupStoreAuth.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_AUTH_PROPERTIES);
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

  public enum GcsBackupStoreAuth {
    NONE,
    AUTO
  }
}
