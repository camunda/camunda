/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;

public class Filter extends BaseExternalCodeConfiguration {

  public FilterCfg toFilterCfg() {
    final var filterCfg = new FilterCfg();
    filterCfg.setId(getId());
    filterCfg.setJarPath(getJarPath());
    filterCfg.setClassName(getClassName());
    return filterCfg;
  }
}
