/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.net.HttpHeaders;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseHeadersConfiguration {

  private static final String HEADER_DELIMITER = "; ";

  @JsonProperty("HSTS.max-age")
  private Long httpStrictTransportSecurityMaxAge;

  @JsonProperty("HSTS.includeSubDomains")
  private Boolean httpStrictTransportSecurityIncludeSubdomains;

  @JsonProperty(HttpHeaders.X_XSS_PROTECTION)
  private String xsssProtection;

  @JsonProperty(HttpHeaders.X_CONTENT_TYPE_OPTIONS)
  private Boolean xContentTypeOptions;

  @JsonProperty(HttpHeaders.CONTENT_SECURITY_POLICY)
  private String contentSecurityPolicy;

  public ResponseHeadersConfiguration() {}

  @SuppressWarnings(SuppressionConstants.UNUSED)
  @JsonProperty("HSTS")
  private void unpackHsts(final Map<String, Object> hsts) {
    httpStrictTransportSecurityMaxAge = Long.valueOf((String) hsts.get("max-age"));
    httpStrictTransportSecurityIncludeSubdomains = (Boolean) hsts.get("includeSubDomains");
  }

  public Long getHttpStrictTransportSecurityMaxAge() {
    return httpStrictTransportSecurityMaxAge;
  }

  @JsonProperty("HSTS.max-age")
  public void setHttpStrictTransportSecurityMaxAge(final Long httpStrictTransportSecurityMaxAge) {
    this.httpStrictTransportSecurityMaxAge = httpStrictTransportSecurityMaxAge;
  }

  public boolean getHttpStrictTransportSecurityIncludeSubdomains() {
    if (httpStrictTransportSecurityIncludeSubdomains == null) {
      return false;
    }

    return httpStrictTransportSecurityIncludeSubdomains;
  }

  @JsonProperty("HSTS.includeSubDomains")
  public void setHttpStrictTransportSecurityIncludeSubdomains(
      final Boolean httpStrictTransportSecurityIncludeSubdomains) {
    this.httpStrictTransportSecurityIncludeSubdomains =
        httpStrictTransportSecurityIncludeSubdomains;
  }

  public String getXsssProtection() {
    return xsssProtection;
  }

  @JsonProperty(HttpHeaders.X_XSS_PROTECTION)
  public void setXsssProtection(final String xsssProtection) {
    this.xsssProtection = xsssProtection;
  }

  public boolean getXContentTypeOptions() {
    if (xContentTypeOptions == null) {
      return false;
    }

    return xContentTypeOptions;
  }

  @JsonProperty(HttpHeaders.X_CONTENT_TYPE_OPTIONS)
  public void setXContentTypeOptions(final Boolean xContentTypeOptions) {
    this.xContentTypeOptions = xContentTypeOptions;
  }

  public String getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  @JsonProperty(HttpHeaders.CONTENT_SECURITY_POLICY)
  public void setContentSecurityPolicy(final String contentSecurityPolicy) {
    this.contentSecurityPolicy = contentSecurityPolicy;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ResponseHeadersConfiguration;
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
    return "ResponseHeadersConfiguration(httpStrictTransportSecurityMaxAge="
        + getHttpStrictTransportSecurityMaxAge()
        + ", httpStrictTransportSecurityIncludeSubdomains="
        + getHttpStrictTransportSecurityIncludeSubdomains()
        + ", xsssProtection="
        + getXsssProtection()
        + ", xContentTypeOptions="
        + getXContentTypeOptions()
        + ", contentSecurityPolicy="
        + getContentSecurityPolicy()
        + ")";
  }

  public Map<String, String> getHeadersWithValues() {
    final Map<String, String> headers = new HashMap<>();

    final List<String> strictTransportSecurityHeaderValues = new ArrayList<>();
    if (getHttpStrictTransportSecurityMaxAge() != null) {
      strictTransportSecurityHeaderValues.add("max-age=" + getHttpStrictTransportSecurityMaxAge());
    }

    if (getHttpStrictTransportSecurityIncludeSubdomains()) {
      strictTransportSecurityHeaderValues.add("includeSubDomains");
    }

    if (!strictTransportSecurityHeaderValues.isEmpty()) {
      headers.put(
          "Strict-Transport-Security",
          String.join(HEADER_DELIMITER, strictTransportSecurityHeaderValues));
    }

    if (getXsssProtection() != null && getXsssProtection().length() > 0) {
      headers.put(HttpHeaders.X_XSS_PROTECTION, getXsssProtection());
    }

    if (getXContentTypeOptions()) {
      headers.put(HttpHeaders.X_CONTENT_TYPE_OPTIONS, "nosniff");
    }

    if (getContentSecurityPolicy() != null && getContentSecurityPolicy().length() > 0) {
      headers.put(HttpHeaders.CONTENT_SECURITY_POLICY, getContentSecurityPolicy());
    }

    return headers;
  }
}
