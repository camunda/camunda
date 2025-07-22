/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

public class MultiTenancyConfiguration {

  public static final String API_ENABLED_PROPERTY = "camunda.security.multiTenancy.apiEnabled";
  private static final boolean DEFAULT_MULTITENANCY_ENABLED = false;
  private static final boolean DEFAULT_API_ENABLED = true;

  private boolean enabled = DEFAULT_MULTITENANCY_ENABLED;
  private boolean apiEnabled = DEFAULT_API_ENABLED;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isApiEnabled() {
    return apiEnabled;
  }

  public void setApiEnabled(final boolean apiEnabled) {
    this.apiEnabled = apiEnabled;
  }
}
