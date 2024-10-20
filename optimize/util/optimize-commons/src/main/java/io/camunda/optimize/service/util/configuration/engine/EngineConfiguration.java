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

  private static DefaultTenant $default$defaultTenant() {
    return new DefaultTenant();
  }

  private static boolean $default$importEnabled() {
    return false;
  }

  private static boolean $default$eventImportEnabled() {
    return false;
  }

  private static List<String> $default$excludedTenants() {
    return new ArrayList<>();
  }

  public static EngineConfigurationBuilder builder() {
    return new EngineConfigurationBuilder();
  }

  public static class EngineConfigurationBuilder {

    private String name;
    private DefaultTenant defaultTenant$value;
    private boolean defaultTenant$set;
    private String rest;
    private EngineWebappsConfiguration webapps;
    private boolean importEnabled$value;
    private boolean importEnabled$set;
    private boolean eventImportEnabled$value;
    private boolean eventImportEnabled$set;
    private List<String> excludedTenants$value;
    private boolean excludedTenants$set;
    private EngineAuthenticationConfiguration authentication;

    EngineConfigurationBuilder() {}

    public EngineConfigurationBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public EngineConfigurationBuilder defaultTenant(final DefaultTenant defaultTenant) {
      defaultTenant$value = defaultTenant;
      defaultTenant$set = true;
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
      importEnabled$value = importEnabled;
      importEnabled$set = true;
      return this;
    }

    public EngineConfigurationBuilder eventImportEnabled(final boolean eventImportEnabled) {
      eventImportEnabled$value = eventImportEnabled;
      eventImportEnabled$set = true;
      return this;
    }

    public EngineConfigurationBuilder excludedTenants(final List<String> excludedTenants) {
      excludedTenants$value = excludedTenants;
      excludedTenants$set = true;
      return this;
    }

    public EngineConfigurationBuilder authentication(
        final EngineAuthenticationConfiguration authentication) {
      this.authentication = authentication;
      return this;
    }

    public EngineConfiguration build() {
      DefaultTenant defaultTenant$value = this.defaultTenant$value;
      if (!defaultTenant$set) {
        defaultTenant$value = EngineConfiguration.$default$defaultTenant();
      }
      boolean importEnabled$value = this.importEnabled$value;
      if (!importEnabled$set) {
        importEnabled$value = EngineConfiguration.$default$importEnabled();
      }
      boolean eventImportEnabled$value = this.eventImportEnabled$value;
      if (!eventImportEnabled$set) {
        eventImportEnabled$value = EngineConfiguration.$default$eventImportEnabled();
      }
      List<String> excludedTenants$value = this.excludedTenants$value;
      if (!excludedTenants$set) {
        excludedTenants$value = EngineConfiguration.$default$excludedTenants();
      }
      return new EngineConfiguration(
          name,
          defaultTenant$value,
          rest,
          webapps,
          importEnabled$value,
          eventImportEnabled$value,
          excludedTenants$value,
          authentication);
    }

    @Override
    public String toString() {
      return "EngineConfiguration.EngineConfigurationBuilder(name="
          + name
          + ", defaultTenant$value="
          + defaultTenant$value
          + ", rest="
          + rest
          + ", webapps="
          + webapps
          + ", importEnabled$value="
          + importEnabled$value
          + ", eventImportEnabled$value="
          + eventImportEnabled$value
          + ", excludedTenants$value="
          + excludedTenants$value
          + ", authentication="
          + authentication
          + ")";
    }
  }
}
