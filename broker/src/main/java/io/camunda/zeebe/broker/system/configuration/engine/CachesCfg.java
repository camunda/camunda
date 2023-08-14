/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration.engine;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import io.camunda.zeebe.engine.EngineConfiguration;

public final class CachesCfg implements ConfigurationEntry {
  private int drgCacheCapacity = EngineConfiguration.DEFAULT_DRG_CACHE_CAPACITY;

  public int getDrgCacheCapacity() {
    return drgCacheCapacity;
  }

  public void setDrgCacheCapacity(final int drgCacheCapacity) {
    this.drgCacheCapacity = drgCacheCapacity;
  }

  @Override
  public String toString() {
    return "CachesCfg{" + "drgCacheCapacity=" + drgCacheCapacity + '}';
  }
}
