/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import java.util.List;

public class Backup {
  // TODO if POC has consensus: Better use enum with toString conversion.
  public static final String STORE_TYPE_NONE = "none";
  public static final String STORE_TYPE_GCS = "gcs";
  public static final String STORE_TYPE_AZURE = "azure";
  public static final String STORE_TYPE_S3 = "s3";
  public static final String STORE_TYPE_FILESYSTEM = "filesystem";

  private static final List<String> VALID_STORE_TYPES = List.of(
      STORE_TYPE_NONE,
      STORE_TYPE_GCS,
      STORE_TYPE_AZURE,
      STORE_TYPE_S3,
      STORE_TYPE_FILESYSTEM
  );

  private String storeType = STORE_TYPE_NONE;

  public AzureStore getAzure() {
    return azure;
  }

  public void setAzure(final AzureStore azure) {
    this.azure = azure;
  }

  private AzureStore azure;

  public String getStoreType() {
    return FallbackConfig.getString("zeebe.broker.data.backup.store", storeType);
  }

  public void setStoreType(String storeType) {
    if (!VALID_STORE_TYPES.contains(storeType)) {
      throw new IllegalStateException("Invalid store type: " + storeType);
    }

    this.storeType = storeType;
  }
}
