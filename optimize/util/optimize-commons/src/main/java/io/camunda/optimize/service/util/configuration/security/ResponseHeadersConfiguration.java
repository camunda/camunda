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
    final int PRIME = 59;
    int result = 1;
    final Object $httpStrictTransportSecurityMaxAge = getHttpStrictTransportSecurityMaxAge();
    result =
        result * PRIME
            + ($httpStrictTransportSecurityMaxAge == null
                ? 43
                : $httpStrictTransportSecurityMaxAge.hashCode());
    final Object $httpStrictTransportSecurityIncludeSubdomains =
        getHttpStrictTransportSecurityIncludeSubdomains();
    result =
        result * PRIME
            + ($httpStrictTransportSecurityIncludeSubdomains == null
                ? 43
                : $httpStrictTransportSecurityIncludeSubdomains.hashCode());
    final Object $xsssProtection = getXsssProtection();
    result = result * PRIME + ($xsssProtection == null ? 43 : $xsssProtection.hashCode());
    final Object $xContentTypeOptions = getXContentTypeOptions();
    result = result * PRIME + ($xContentTypeOptions == null ? 43 : $xContentTypeOptions.hashCode());
    final Object $contentSecurityPolicy = getContentSecurityPolicy();
    result =
        result * PRIME + ($contentSecurityPolicy == null ? 43 : $contentSecurityPolicy.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ResponseHeadersConfiguration)) {
      return false;
    }
    final ResponseHeadersConfiguration other = (ResponseHeadersConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$httpStrictTransportSecurityMaxAge = getHttpStrictTransportSecurityMaxAge();
    final Object other$httpStrictTransportSecurityMaxAge =
        other.getHttpStrictTransportSecurityMaxAge();
    if (this$httpStrictTransportSecurityMaxAge == null
        ? other$httpStrictTransportSecurityMaxAge != null
        : !this$httpStrictTransportSecurityMaxAge.equals(other$httpStrictTransportSecurityMaxAge)) {
      return false;
    }
    final Object this$httpStrictTransportSecurityIncludeSubdomains =
        getHttpStrictTransportSecurityIncludeSubdomains();
    final Object other$httpStrictTransportSecurityIncludeSubdomains =
        other.getHttpStrictTransportSecurityIncludeSubdomains();
    if (this$httpStrictTransportSecurityIncludeSubdomains == null
        ? other$httpStrictTransportSecurityIncludeSubdomains != null
        : !this$httpStrictTransportSecurityIncludeSubdomains.equals(
            other$httpStrictTransportSecurityIncludeSubdomains)) {
      return false;
    }
    final Object this$xsssProtection = getXsssProtection();
    final Object other$xsssProtection = other.getXsssProtection();
    if (this$xsssProtection == null
        ? other$xsssProtection != null
        : !this$xsssProtection.equals(other$xsssProtection)) {
      return false;
    }
    final Object this$xContentTypeOptions = getXContentTypeOptions();
    final Object other$xContentTypeOptions = other.getXContentTypeOptions();
    if (this$xContentTypeOptions == null
        ? other$xContentTypeOptions != null
        : !this$xContentTypeOptions.equals(other$xContentTypeOptions)) {
      return false;
    }
    final Object this$contentSecurityPolicy = getContentSecurityPolicy();
    final Object other$contentSecurityPolicy = other.getContentSecurityPolicy();
    if (this$contentSecurityPolicy == null
        ? other$contentSecurityPolicy != null
        : !this$contentSecurityPolicy.equals(other$contentSecurityPolicy)) {
      return false;
    }
    return true;
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
