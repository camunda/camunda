/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

public class Backup {
  /** Configuration for backup store AWS S3 */
  private S3 s3 = new S3();

  /** Configuration for backup store GCS */
  private Gcs gcs = new Gcs();

  /** Configuration for backup store Azure */
  private Azure azure = new Azure();

  public S3 getS3() {
    return s3;
  }

  public void setS3(final S3 s3) {
    this.s3 = s3;
  }

  public Gcs getGcs() {
    return gcs;
  }

  public void setGcs(final Gcs gcs) {
    this.gcs = gcs;
  }

  public Azure getAzure() {
    return azure;
  }

  public void setAzure(final Azure azure) {
    this.azure = azure;
  }
}
