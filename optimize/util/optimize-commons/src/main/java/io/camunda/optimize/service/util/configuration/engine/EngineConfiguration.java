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

public class EngineConfiguration {

  private String name;
  private DefaultTenant defaultTenant = new DefaultTenant();
  private String rest;
  private EngineWebappsConfiguration webapps;
  private boolean importEnabled = false;
  private List<String> excludedTenants = new ArrayList<>();

  private EngineAuthenticationConfiguration authentication;

  public EngineConfiguration(
      final String name,
      final DefaultTenant defaultTenant,
      final String rest,
      final EngineWebappsConfiguration webapps,
      final boolean importEnabled,
      final boolean eventImportEnabled,
      final List<String> excludedTenants,
      final EngineAuthenticationConfiguration authentication) {
    this.name = name;
    this.defaultTenant = defaultTenant;
    this.rest = rest;
    this.webapps = webapps;
    this.importEnabled = importEnabled;
    this.excludedTenants = excludedTenants;
    this.authentication = authentication;
  }

  protected EngineConfiguration() {}

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

  public void setExcludedTenants(final List<String> excludedTenants) {
    this.excludedTenants = excludedTenants;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public DefaultTenant getDefaultTenant() {
    return defaultTenant;
  }

  public void setDefaultTenant(final DefaultTenant defaultTenant) {
    this.defaultTenant = defaultTenant;
  }

  public String getRest() {
    return rest;
  }

  public void setRest(final String rest) {
    this.rest = ConfigurationUtil.cutTrailingSlash(rest);
  }

  public EngineWebappsConfiguration getWebapps() {
    return webapps;
  }

  public void setWebapps(final EngineWebappsConfiguration webapps) {
    this.webapps = webapps;
  }

  public boolean isImportEnabled() {
    return importEnabled;
  }

  public void setImportEnabled(final boolean importEnabled) {
    this.importEnabled = importEnabled;
  }

  public EngineAuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final EngineAuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EngineConfiguration;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "EngineConfiguration(name="
        + getName()
        + ", defaultTenant="
        + getDefaultTenant()
        + ", rest="
        + getRest()
        + ", webapps="
        + getWebapps()
        + ", importEnabled="
        + isImportEnabled()
        + ", excludedTenants="
        + getExcludedTenants()
        + ", authentication="
        + getAuthentication()
        + ")";
  }

  private static DefaultTenant defaultDefaultTenant() {
    return new DefaultTenant();
  }

  private static boolean defaultImportEnabled() {
    return false;
  }

  private static boolean defaultEventImportEnabled() {
    return false;
  }

  private static List<String> defaultExcludedTenants() {
    return new ArrayList<>();
  }

  public static EngineConfigurationBuilder builder() {
    return new EngineConfigurationBuilder();
  }

  public static class EngineConfigurationBuilder {

    private String name;
    private DefaultTenant defaultTenantValue;
    private boolean defaultTenantSet;
    private String rest;
    private EngineWebappsConfiguration webapps;
    private boolean importEnabledValue;
    private boolean importEnabledSet;
    private boolean eventImportEnabledValue;
    private boolean eventImportEnabledSet;
    private List<String> excludedTenantsValue;
    private boolean excludedTenantsSet;
    private EngineAuthenticationConfiguration authentication;

    EngineConfigurationBuilder() {}

    public EngineConfigurationBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public EngineConfigurationBuilder defaultTenant(final DefaultTenant defaultTenant) {
      defaultTenantValue = defaultTenant;
      defaultTenantSet = true;
      return this;
    }

    public EngineConfigurationBuilder rest(final String rest) {
      this.rest = rest;
      return this;
    }

    public EngineConfigurationBuilder webapps(final EngineWebappsConfiguration webapps) {
      this.webapps = webapps;
      return this;
    }

    public EngineConfigurationBuilder importEnabled(final boolean importEnabled) {
      importEnabledValue = importEnabled;
      importEnabledSet = true;
      return this;
    }

    public EngineConfigurationBuilder eventImportEnabled(final boolean eventImportEnabled) {
      eventImportEnabledValue = eventImportEnabled;
      eventImportEnabledSet = true;
      return this;
    }

    public EngineConfigurationBuilder excludedTenants(final List<String> excludedTenants) {
      excludedTenantsValue = excludedTenants;
      excludedTenantsSet = true;
      return this;
    }

    public EngineConfigurationBuilder authentication(
        final EngineAuthenticationConfiguration authentication) {
      this.authentication = authentication;
      return this;
    }

    public EngineConfiguration build() {
      DefaultTenant defaultTenantValue = this.defaultTenantValue;
      if (!defaultTenantSet) {
        defaultTenantValue = EngineConfiguration.defaultDefaultTenant();
      }
      boolean importEnabledValue = this.importEnabledValue;
      if (!importEnabledSet) {
        importEnabledValue = EngineConfiguration.defaultImportEnabled();
      }
      boolean eventImportEnabledValue = this.eventImportEnabledValue;
      if (!eventImportEnabledSet) {
        eventImportEnabledValue = EngineConfiguration.defaultEventImportEnabled();
      }
      List<String> excludedTenantsValue = this.excludedTenantsValue;
      if (!excludedTenantsSet) {
        excludedTenantsValue = EngineConfiguration.defaultExcludedTenants();
      }
      return new EngineConfiguration(
          name,
          defaultTenantValue,
          rest,
          webapps,
          importEnabledValue,
          eventImportEnabledValue,
          excludedTenantsValue,
          authentication);
    }

    @Override
    public String toString() {
      return "EngineConfiguration.EngineConfigurationBuilder(name="
          + name
          + ", defaultTenantValue="
          + defaultTenantValue
          + ", rest="
          + rest
          + ", webapps="
          + webapps
          + ", importEnabledValue="
          + importEnabledValue
          + ", eventImportEnabledValue="
          + eventImportEnabledValue
          + ", excludedTenantsValue="
          + excludedTenantsValue
          + ", authentication="
          + authentication
          + ")";
    }
  }
}
