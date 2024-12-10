/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public class BasicAuthConfiguration {
  private boolean httpBasicAuthEnabled = true;

  public boolean isHttpBasicAuthEnabled() {
    return httpBasicAuthEnabled;
  }

  public void setHttpBasicAuthEnabled(final boolean httpBasicAuthEnabled) {
    this.httpBasicAuthEnabled = httpBasicAuthEnabled;
  }
}
