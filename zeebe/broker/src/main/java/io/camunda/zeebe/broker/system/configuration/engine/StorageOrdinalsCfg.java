/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public class StorageOrdinalsCfg implements ConfigurationEntry {

  private boolean enableArchiverless =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_ENABLE_ARCHIVERLESS;
  private int fixedStorageOrdinal =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_FIXED_STORAGE_ORDINAL;

  public boolean isEnableArchiverless() {
    return enableArchiverless;
  }

  public void setEnableArchiverless(final boolean enableArchiverless) {
    this.enableArchiverless = enableArchiverless;
  }

  public int getFixedStorageOrdinal() {
    return fixedStorageOrdinal;
  }

  public void setFixedStorageOrdinal(final int fixedStorageOrdinal) {
    this.fixedStorageOrdinal = fixedStorageOrdinal;
  }

  @Override
  public String toString() {
    return "StorageOrdinalsCfg{"
        + "enableArchiverless="
        + enableArchiverless
        + ", fixedStorageOrdinal="
        + fixedStorageOrdinal
        + '}';
  }
}
