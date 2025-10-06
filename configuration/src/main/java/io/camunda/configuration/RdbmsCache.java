/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.exporter.rdbms.ExporterConfiguration;

public class RdbmsCache {

  /**
   * The maximum number of entries the cache may contain. When the size of the cache exceeds this,
   * the oldest entries are removed.
   */
  private int maxSize = ExporterConfiguration.DEFAULT_MAX_CACHE_SIZE;

  public int getMaxSize() {
    return maxSize;
  }

  public void setMaxSize(final int maxSize) {
    this.maxSize = maxSize;
  }
}
