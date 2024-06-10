/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(value = "camunda.presets.identity")
public class IdentityPresets {
  private String user;
  private String password;

  public IdentityPresets() {}

  public IdentityPresets(final String user, final String password) {
    this.user = user;
    this.password = password;
  }

  public String getUser() {
    return user;
  }

  public void setUser(final String user) {
    this.user = user;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }
}
