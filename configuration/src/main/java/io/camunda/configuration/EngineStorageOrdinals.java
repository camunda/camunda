/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.engine.EngineConfiguration;

/**
 * Defines configurations for storage ordinals in the engine. The prefix for this class is
 * camunda.processing.engine.storage-ordinals.
 */
public class EngineStorageOrdinals {

  public static final boolean DEFAULT_ENABLE_ARCHIVERLESS =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_ENABLE_ARCHIVERLESS;
  public static final int DEFAULT_FIXED_STORAGE_ORDINAL_KEY =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_FIXED_STORAGE_ORDINAL_KEY;

  /** Controls whether the archiverless mode is enabled. */
  private boolean enableArchiverless = DEFAULT_ENABLE_ARCHIVERLESS;

  /**
   * Override default storage ordinal key with fixed value, mainly used during initial testing. The
   * default value of -1 means no override is applied.
   */
  private int fixedStorageOrdinalKey = DEFAULT_FIXED_STORAGE_ORDINAL_KEY;

  public boolean isEnableArchiverless() {
    return enableArchiverless;
  }

  public void setEnableArchiverless(final boolean enableArchiverless) {
    this.enableArchiverless = enableArchiverless;
  }

  public int getFixedStorageOrdinalKey() {
    return fixedStorageOrdinalKey;
  }

  public void setFixedStorageOrdinalKey(final int fixedStorageOrdinalKey) {
    this.fixedStorageOrdinalKey = fixedStorageOrdinalKey;
  }
}
