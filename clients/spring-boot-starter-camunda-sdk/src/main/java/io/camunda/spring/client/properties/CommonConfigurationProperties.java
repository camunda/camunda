/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import io.camunda.spring.client.properties.common.Client;
import io.camunda.spring.client.properties.common.Keycloak;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "common")
@Deprecated
public class CommonConfigurationProperties extends Client {

  @NestedConfigurationProperty private Keycloak keycloak = new Keycloak();

  @Override
  public String toString() {
    return "CommonConfigurationProperties{" + "keycloak=" + keycloak + "} " + super.toString();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "not required")
  public Boolean getEnabled() {
    return super.getEnabled();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "not required")
  public String getUrl() {
    return super.getUrl();
  }

  @Override
  @DeprecatedConfigurationProperty(replacement = "not required")
  public String getBaseUrl() {
    return super.getBaseUrl();
  }

  @DeprecatedConfigurationProperty(
      replacement = "not required",
      reason = "Please use 'camunda.client.auth'")
  public Keycloak getKeycloak() {
    return keycloak;
  }

  public void setKeycloak(final Keycloak keycloak) {
    this.keycloak = keycloak;
  }
}
