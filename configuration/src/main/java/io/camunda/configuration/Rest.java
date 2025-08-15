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

public class Rest {

  /** Set the filters */
  private List<Filter> filters = new ArrayList<>();

  /** Set the process cache configuration */
  private ProcessCache processCache = new ProcessCache();

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
}
