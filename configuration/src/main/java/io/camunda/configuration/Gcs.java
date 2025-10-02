/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class Gcs {
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

  public enum GcsBackupStoreAuth {
    NONE,
    AUTO
  }
}
