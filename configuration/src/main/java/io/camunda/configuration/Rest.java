/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Rest {

  /** Set the filters */
  @NestedConfigurationProperty private List<Filter> filters = new ArrayList<>();

  /** Set the process cache configuration */
  @NestedConfigurationProperty private ProcessCache processCache = new ProcessCache();

  /** Set the executor configuration */
  @NestedConfigurationProperty private Executor executor = new Executor();

  public List<Filter> getFilters() {
    return filters;
  }

  public void setFilters(final List<Filter> filters) {
    this.filters = filters;
  }

  public ProcessCache getProcessCache() {
    return processCache;
  }

  public void setProcessCache(final ProcessCache processCache) {
    this.processCache = processCache;
  }

  public Executor getExecutor() {
    return executor;
  }

  public void setExecutor(final Executor executor) {
    this.executor = executor;
  }
}
