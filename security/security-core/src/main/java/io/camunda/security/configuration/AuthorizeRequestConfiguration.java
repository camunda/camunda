/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.HashMap;
import java.util.Map;

public class AuthorizeRequestConfiguration {
  private Map<String, Object> additionalParameters;

  public Map<String, Object> getAdditionalParameters() {
    return additionalParameters;
  }

  public void setAdditionalParameters(final Map<String, Object> additionalParameters) {
    this.additionalParameters = additionalParameters;
  }

  public boolean isSet() {
    return additionalParameters != null;
  }

  public static Builder builder() {
    return new Builder();
  }

  // Builder class
  public static class Builder {
    private final Map<String, Object> additionalParameters;

    public Builder() {
      additionalParameters = new HashMap<>();
    }

    public Builder additionalParameter(final String key, final Object value) {
      additionalParameters.put(key, value);
      return this;
    }

    public Builder additionalParameters(final Map<String, Object> additionalParameters) {
      this.additionalParameters.putAll(additionalParameters);
      return this;
    }

    public AuthorizeRequestConfiguration build() {
      final AuthorizeRequestConfiguration config = new AuthorizeRequestConfiguration();
      config.setAdditionalParameters(additionalParameters);
      return config;
    }
  }
}
