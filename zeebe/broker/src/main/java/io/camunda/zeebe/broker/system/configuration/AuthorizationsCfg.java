/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.engine.EngineConfiguration;

public final class AuthorizationsCfg implements ConfigurationEntry {

  private boolean enableAuthorization = EngineConfiguration.DEFAULT_ENABLE_AUTHORIZATION_CHECKS;

  @Override
  public String toString() {
    return "AuthorizationCfg{enableAuthorization=%s}".formatted(enableAuthorization);
  }

  public boolean isEnableAuthorization() {
    return enableAuthorization;
  }

  public void setEnableAuthorization(final boolean enableAuthorization) {
    this.enableAuthorization = enableAuthorization;
  }
}
