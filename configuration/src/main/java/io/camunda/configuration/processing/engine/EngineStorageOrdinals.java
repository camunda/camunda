/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processing.engine;

import io.camunda.zeebe.engine.EngineConfiguration;
import java.util.List;

/**
 * Defines configurations for storage ordinals in the engine. The prefix for this class is
 * camunda.processing.engine.storage-ordinals.
 */
public class EngineStorageOrdinals {

  public static final boolean DEFAULT_ENABLE_ARCHIVERLESS =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_ENABLE_ARCHIVERLESS;
  public static final List<Integer> DEFAULT_FIXED_STORAGE_ORDINALS =
      EngineConfiguration.DEFAULT_ENGINE_STORAGE_ORDINALS_FIXED_STORAGE_ORDINALS;

  /** Controls whether the archiverless mode is enabled. */
  private boolean enableArchiverless = DEFAULT_ENABLE_ARCHIVERLESS;

  /**
   * Override default storage ordinals with a fixed set of values, given as a comma-separated list
   * of integers (e.g. 1001,1002), mainly used during initial testing. When more than one value is
   * given, one is in a round-robin fashion for new process instances. The default value of -1 means
   * no override is applied.
   */
  private List<Integer> fixedStorageOrdinals = DEFAULT_FIXED_STORAGE_ORDINALS;

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
}
