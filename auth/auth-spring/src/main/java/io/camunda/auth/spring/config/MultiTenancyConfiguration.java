/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.config;

public class MultiTenancyConfiguration {
  private boolean checksEnabled = false;
  private boolean apiEnabled = true;

  public boolean isChecksEnabled() { return checksEnabled; }
  public void setChecksEnabled(final boolean checksEnabled) { this.checksEnabled = checksEnabled; }
  public boolean isApiEnabled() { return apiEnabled; }
  public void setApiEnabled(final boolean apiEnabled) { this.apiEnabled = apiEnabled; }
}
