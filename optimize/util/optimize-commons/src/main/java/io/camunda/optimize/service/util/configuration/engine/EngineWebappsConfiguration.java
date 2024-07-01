/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import io.camunda.optimize.service.util.configuration.ConfigurationUtil;
import lombok.Data;

@Data
public class EngineWebappsConfiguration {

  private String endpoint;
  private boolean enabled;

  public void setEndpoint(String endpoint) {
    this.endpoint = ConfigurationUtil.cutTrailingSlash(endpoint);
  }
}
