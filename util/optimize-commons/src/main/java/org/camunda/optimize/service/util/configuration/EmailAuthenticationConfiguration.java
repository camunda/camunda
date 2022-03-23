/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.EMAIL_AUTHENTICATION;

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
      throw new OptimizeConfigurationException(EMAIL_AUTHENTICATION + ".securityProtocol must be set if authentication enabled");
    }
  }

}
