/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.Data;

import java.util.Optional;

@Data
public class EngineConfiguration {

  private String name;
  private String defaultTenantId;
  private String rest;
  private EngineWebappsConfiguration webapps;
  private boolean importEnabled = true;

  private EngineAuthenticationConfiguration authentication;

  public Optional<String> getDefaultTenantId() {
    return Optional.ofNullable(defaultTenantId);
  }

  public void setRest(String rest) {
    this.rest = ConfigurationUtil.cutTrailingSlash(rest);
  }

}
