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
import lombok.Data;

@Data
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

  @SuppressWarnings(SuppressionConstants.UNUSED)
  @JsonProperty("HSTS")
  private void unpackHsts(final Map<String, Object> hsts) {
    this.httpStrictTransportSecurityMaxAge = Long.valueOf((String) hsts.get("max-age"));
    this.httpStrictTransportSecurityIncludeSubdomains = (Boolean) hsts.get("includeSubDomains");
  }
}
