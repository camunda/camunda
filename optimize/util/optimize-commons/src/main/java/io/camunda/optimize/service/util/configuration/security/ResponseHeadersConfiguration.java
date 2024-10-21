/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.util.SuppressionConstants;
import java.util.Map;

public class ResponseHeadersConfiguration {

  @JsonProperty("HSTS.max-age")
  private Long httpStrictTransportSecurityMaxAge;

  @JsonProperty("HSTS.includeSubDomains")
  private Boolean httpStrictTransportSecurityIncludeSubdomains;

  @JsonProperty("X-XSS-Protection")
  private String xsssProtection;

  @JsonProperty("X-Content-Type-Options")
  private Boolean xContentTypeOptions;

  @JsonProperty("Content-Security-Policy")
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

  public Boolean getHttpStrictTransportSecurityIncludeSubdomains() {
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

  @JsonProperty("X-XSS-Protection")
  public void setXsssProtection(final String xsssProtection) {
    this.xsssProtection = xsssProtection;
  }

  public Boolean getXContentTypeOptions() {
    return xContentTypeOptions;
  }

  @JsonProperty("X-Content-Type-Options")
  public void setXContentTypeOptions(final Boolean xContentTypeOptions) {
    this.xContentTypeOptions = xContentTypeOptions;
  }

  public String getContentSecurityPolicy() {
    return contentSecurityPolicy;
  }

  @JsonProperty("Content-Security-Policy")
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
}
