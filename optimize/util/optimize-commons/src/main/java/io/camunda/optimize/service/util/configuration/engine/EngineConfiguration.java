/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.service.util.configuration.ConfigurationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EngineConfiguration {

  private String name;
  @Builder.Default private DefaultTenant defaultTenant = new DefaultTenant();
  private String rest;
  private EngineWebappsConfiguration webapps;
  @Builder.Default private boolean importEnabled = false;
  @Builder.Default private List<String> excludedTenants = new ArrayList<>();

  private EngineAuthenticationConfiguration authentication;

  public EngineConfiguration(
      String name,
      DefaultTenant defaultTenant,
      String rest,
      EngineWebappsConfiguration webapps,
      boolean importEnabled,
      List<String> excludedTenants,
      EngineAuthenticationConfiguration authentication) {
    this.name = name;
    this.defaultTenant = defaultTenant;
    this.rest = rest;
    this.webapps = webapps;
    this.importEnabled = importEnabled;
    this.excludedTenants = excludedTenants;
    this.authentication = authentication;
  }

  @JsonIgnore
  public Optional<String> getDefaultTenantId() {
    return Optional.ofNullable(defaultTenant).map(DefaultTenant::getId);
  }

  @JsonIgnore
  public Optional<String> getDefaultTenantName() {
    return Optional.ofNullable(defaultTenant).map(DefaultTenant::getName);
  }

  public List<String> getExcludedTenants() {
    return excludedTenants;
  }

  public void setRest(String rest) {
    this.rest = ConfigurationUtil.cutTrailingSlash(rest);
  }
}
