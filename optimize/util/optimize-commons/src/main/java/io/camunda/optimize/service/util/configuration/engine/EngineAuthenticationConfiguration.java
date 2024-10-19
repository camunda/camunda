/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

public class EngineAuthenticationConfiguration {

  private boolean enabled;
  private String password;
  private String user;

  public EngineAuthenticationConfiguration(
      final boolean enabled, final String password, final String user) {
    this.enabled = enabled;
    this.password = password;
    this.user = user;
  }

  protected EngineAuthenticationConfiguration() {}

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public String getUser() {
    return user;
  }

  public void setUser(final String user) {
    this.user = user;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EngineAuthenticationConfiguration;
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
    return "EngineAuthenticationConfiguration(enabled="
        + isEnabled()
        + ", password="
        + getPassword()
        + ", user="
        + getUser()
        + ")";
  }

  public static EngineAuthenticationConfigurationBuilder builder() {
    return new EngineAuthenticationConfigurationBuilder();
  }

  public static class EngineAuthenticationConfigurationBuilder {

    private boolean enabled;
    private String password;
    private String user;

    EngineAuthenticationConfigurationBuilder() {}

    public EngineAuthenticationConfigurationBuilder enabled(final boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public EngineAuthenticationConfigurationBuilder password(final String password) {
      this.password = password;
      return this;
    }

    public EngineAuthenticationConfigurationBuilder user(final String user) {
      this.user = user;
      return this;
    }

    public EngineAuthenticationConfiguration build() {
      return new EngineAuthenticationConfiguration(enabled, password, user);
    }

    @Override
    public String toString() {
      return "EngineAuthenticationConfiguration.EngineAuthenticationConfigurationBuilder(enabled="
          + enabled
          + ", password="
          + password
          + ", user="
          + user
          + ")";
    }
  }
}
