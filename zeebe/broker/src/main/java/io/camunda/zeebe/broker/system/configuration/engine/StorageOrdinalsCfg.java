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
import java.util.List;

public class StorageOrdinalsCfg implements ConfigurationEntry {

  private boolean enableArchiverless =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_ENABLE_ARCHIVERLESS;
  private List<Integer> fixedStorageOrdinals =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_FIXED_STORAGE_ORDINALS;

  public boolean isEnableArchiverless() {
    return enableArchiverless;
  }

  public void setEnableArchiverless(final boolean enableArchiverless) {
    this.enableArchiverless = enableArchiverless;
  }

  public List<Integer> getFixedStorageOrdinals() {
    return fixedStorageOrdinals;
  }

  public void setFixedStorageOrdinals(final List<Integer> fixedStorageOrdinals) {
    this.fixedStorageOrdinals = fixedStorageOrdinals;
  }

  @Override
  public String toString() {
    return "StorageOrdinalsCfg{"
        + "enableArchiverless="
        + enableArchiverless
        + ", fixedStorageOrdinals="
        + fixedStorageOrdinals
        + '}';
  }
}
