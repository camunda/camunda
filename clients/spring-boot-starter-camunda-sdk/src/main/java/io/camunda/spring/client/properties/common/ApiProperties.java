/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties.common;

import java.net.URL;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@Deprecated(forRemoval = true, since = "8.7")
public class ApiProperties {
  private Boolean enabled;
  @Deprecated private URL baseUrl;

  private String audience;
  private String scope;

  @DeprecatedConfigurationProperty(replacement = "camunda.client.enabled")
  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(final Boolean enabled) {
    this.enabled = enabled;
  }

  @Deprecated
  @DeprecatedConfigurationProperty(replacement = "camunda.client.restAddress")
  public URL getBaseUrl() {
    return baseUrl;
  }

  @Deprecated
  public void setBaseUrl(final URL baseUrl) {
    this.baseUrl = baseUrl;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.audience")
  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  @DeprecatedConfigurationProperty(replacement = "camunda.client.auth.scope")
  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }
}
