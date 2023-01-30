/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.camunda.optimize.util.SuppressionConstants;

import java.util.Map;

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
