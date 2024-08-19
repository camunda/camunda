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
    final int PRIME = 59;
    int result = 1;
    final Object $auth = getAuth();
    result = result * PRIME + ($auth == null ? 43 : $auth.hashCode());
    final Object $license = getLicense();
    result = result * PRIME + ($license == null ? 43 : $license.hashCode());
    final Object $responseHeaders = getResponseHeaders();
    result = result * PRIME + ($responseHeaders == null ? 43 : $responseHeaders.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SecurityConfiguration)) {
      return false;
    }
    final SecurityConfiguration other = (SecurityConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$auth = getAuth();
    final Object other$auth = other.getAuth();
    if (this$auth == null ? other$auth != null : !this$auth.equals(other$auth)) {
      return false;
    }
    final Object this$license = getLicense();
    final Object other$license = other.getLicense();
    if (this$license == null ? other$license != null : !this$license.equals(other$license)) {
      return false;
    }
    final Object this$responseHeaders = getResponseHeaders();
    final Object other$responseHeaders = other.getResponseHeaders();
    if (this$responseHeaders == null
        ? other$responseHeaders != null
        : !this$responseHeaders.equals(other$responseHeaders)) {
      return false;
    }
    return true;
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
