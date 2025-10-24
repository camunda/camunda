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

public final class CachesCfg implements ConfigurationEntry {
  private int drgCacheCapacity = EngineConfiguration.DEFAULT_DRG_CACHE_CAPACITY;
  private int formCacheCapacity = EngineConfiguration.DEFAULT_FORM_CACHE_CAPACITY;
  private int processCacheCapacity = EngineConfiguration.DEFAULT_PROCESS_CACHE_CAPACITY;
  private int resourceCacheCapacity = EngineConfiguration.DEFAULT_PROCESS_CACHE_CAPACITY;
  private int authorizationsCacheCapacity =
      EngineConfiguration.DEFAULT_AUTHORIZATIONS_CACHE_CAPACITY;

  public int getDrgCacheCapacity() {
    return drgCacheCapacity;
  }

  public void setDrgCacheCapacity(final int drgCacheCapacity) {
    this.drgCacheCapacity = drgCacheCapacity;
  }

  public int getFormCacheCapacity() {
    return formCacheCapacity;
  }

  public void setFormCacheCapacity(final int formCacheCapacity) {
    this.formCacheCapacity = formCacheCapacity;
  }

  public int getProcessCacheCapacity() {
    return processCacheCapacity;
  }

  public void setProcessCacheCapacity(final int processCacheCapacity) {
    this.processCacheCapacity = processCacheCapacity;
  }

  public int getResourceCacheCapacity() {
    return resourceCacheCapacity;
  }

  public void setResourceCacheCapacity(final int resourceCacheCapacity) {
    this.resourceCacheCapacity = resourceCacheCapacity;
  }

  public int getAuthorizationsCacheCapacity() {
    return authorizationsCacheCapacity;
  }

  public void setAuthorizationsCacheCapacity(final int authorizationsCacheCapacity) {
    this.authorizationsCacheCapacity = authorizationsCacheCapacity;
  }

  @Override
  public String toString() {
    return "CachesCfg{"
        + "drgCacheCapacity="
        + drgCacheCapacity
        + ", formCacheCapacity="
        + formCacheCapacity
        + ", processCacheCapacity="
        + processCacheCapacity
        + ", resourceCacheCapacity="
        + resourceCacheCapacity
        + ", authorizationsCacheCapacity="
        + authorizationsCacheCapacity
        + '}';
  }
}
