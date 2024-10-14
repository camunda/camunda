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
    final int PRIME = 59;
    int result = 1;
    result = result * PRIME + (isEnabled() ? 79 : 97);
    final Object $password = getPassword();
    result = result * PRIME + ($password == null ? 43 : $password.hashCode());
    final Object $user = getUser();
    result = result * PRIME + ($user == null ? 43 : $user.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EngineAuthenticationConfiguration)) {
      return false;
    }
    final EngineAuthenticationConfiguration other = (EngineAuthenticationConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (isEnabled() != other.isEnabled()) {
      return false;
    }
    final Object this$password = getPassword();
    final Object other$password = other.getPassword();
    if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
      return false;
    }
    final Object this$user = getUser();
    final Object other$user = other.getUser();
    if (this$user == null ? other$user != null : !this$user.equals(other$user)) {
      return false;
    }
    return true;
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
