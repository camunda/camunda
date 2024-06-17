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
import lombok.Data;

@Data
public class EmailAuthenticationConfiguration {
  @JsonProperty("enabled")
  private Boolean enabled;

  @JsonProperty("username")
  private String username;

  @JsonProperty("password")
  private String password;

  @JsonProperty("securityProtocol")
  private EmailSecurityProtocol securityProtocol;

  public void validate() {
    if (enabled && securityProtocol == null) {
      throw new OptimizeConfigurationException(
          EMAIL_AUTHENTICATION + ".securityProtocol must be set if authentication enabled");
    }
  }
}
