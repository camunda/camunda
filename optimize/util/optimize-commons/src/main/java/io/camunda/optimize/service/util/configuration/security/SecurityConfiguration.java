/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

public class SecurityConfiguration {

  private AuthConfiguration auth;
  private LicenseConfiguration license;
  private ResponseHeadersConfiguration responseHeaders;

  public SecurityConfiguration() {}

  public AuthConfiguration getAuth() {
    return auth;
  }

  public void setAuth(final AuthConfiguration auth) {
    this.auth = auth;
  }

  public LicenseConfiguration getLicense() {
    return license;
  }

  public void setLicense(final LicenseConfiguration license) {
    this.license = license;
  }

  public ResponseHeadersConfiguration getResponseHeaders() {
    return responseHeaders;
  }

  public void setResponseHeaders(final ResponseHeadersConfiguration responseHeaders) {
    this.responseHeaders = responseHeaders;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof SecurityConfiguration;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "SecurityConfiguration(auth="
        + getAuth()
        + ", license="
        + getLicense()
        + ", responseHeaders="
        + getResponseHeaders()
        + ")";
  }
}
