/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.EMAIL_AUTHENTICATION;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;

public class EmailAuthenticationConfiguration {

  @JsonProperty("enabled")
  private Boolean enabled;

  @JsonProperty("username")
  private String username;

  @JsonProperty("password")
  private String password;

  @JsonProperty("securityProtocol")
  private EmailSecurityProtocol securityProtocol;

  public EmailAuthenticationConfiguration() {}

  public void validate() {
    if (enabled && securityProtocol == null) {
      throw new OptimizeConfigurationException(
          EMAIL_AUTHENTICATION + ".securityProtocol must be set if authentication enabled");
    }
  }

  public Boolean getEnabled() {
    return enabled;
  }

  @JsonProperty("enabled")
  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  public String getUsername() {
    return username;
  }

  @JsonProperty("username")
  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  @JsonProperty("password")
  public void setPassword(final String password) {
    this.password = password;
  }

  public EmailSecurityProtocol getSecurityProtocol() {
    return securityProtocol;
  }

  @JsonProperty("securityProtocol")
  public void setSecurityProtocol(final EmailSecurityProtocol securityProtocol) {
    this.securityProtocol = securityProtocol;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof EmailAuthenticationConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $enabled = getEnabled();
    result = result * PRIME + ($enabled == null ? 43 : $enabled.hashCode());
    final Object $username = getUsername();
    result = result * PRIME + ($username == null ? 43 : $username.hashCode());
    final Object $password = getPassword();
    result = result * PRIME + ($password == null ? 43 : $password.hashCode());
    final Object $securityProtocol = getSecurityProtocol();
    result = result * PRIME + ($securityProtocol == null ? 43 : $securityProtocol.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EmailAuthenticationConfiguration)) {
      return false;
    }
    final EmailAuthenticationConfiguration other = (EmailAuthenticationConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$enabled = getEnabled();
    final Object other$enabled = other.getEnabled();
    if (this$enabled == null ? other$enabled != null : !this$enabled.equals(other$enabled)) {
      return false;
    }
    final Object this$username = getUsername();
    final Object other$username = other.getUsername();
    if (this$username == null ? other$username != null : !this$username.equals(other$username)) {
      return false;
    }
    final Object this$password = getPassword();
    final Object other$password = other.getPassword();
    if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
      return false;
    }
    final Object this$securityProtocol = getSecurityProtocol();
    final Object other$securityProtocol = other.getSecurityProtocol();
    if (this$securityProtocol == null
        ? other$securityProtocol != null
        : !this$securityProtocol.equals(other$securityProtocol)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EmailAuthenticationConfiguration(enabled="
        + getEnabled()
        + ", username="
        + getUsername()
        + ", password="
        + getPassword()
        + ", securityProtocol="
        + getSecurityProtocol()
        + ")";
  }
}
