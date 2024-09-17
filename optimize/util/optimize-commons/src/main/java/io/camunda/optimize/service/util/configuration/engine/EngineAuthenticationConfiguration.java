/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.engine;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class EngineAuthenticationConfiguration {

  private boolean enabled;
  private String password;
  private String user;

  public EngineAuthenticationConfiguration(boolean enabled, String password, String user) {
    this.enabled = enabled;
    this.password = password;
    this.user = user;
  }

  protected EngineAuthenticationConfiguration() {}
}
