/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

public class ManagementIdentityProperties {
  private String baseUrl;
  private String m2mToken;

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getM2mToken() {
    return m2mToken;
  }

  public void setM2mToken(final String m2mToken) {
    this.m2mToken = m2mToken;
  }
}
