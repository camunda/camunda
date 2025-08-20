/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camunda.rest.swagger")
public class OpenApiConfigurationProperties {

  /** Whether the Swagger documentation is enabled. Default: true */
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }
}
