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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
