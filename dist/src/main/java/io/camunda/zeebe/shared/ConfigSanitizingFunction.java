/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared;

import io.camunda.zeebe.shared.ConfigSanitizingFunction.ConfigSanitizingProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ConfigSanitizingProperties.class)
public final class ConfigSanitizingFunction implements SanitizingFunction {

  private final ConfigSanitizingProperties properties;

  @Autowired
  public ConfigSanitizingFunction(final ConfigSanitizingProperties properties) {
    this.properties = properties;
  }

  @Override
  public SanitizableData apply(final SanitizableData data) {
    final var key = data.getKey();
    if (data.getValue() == null || key == null) {
      return data;
    }

    for (final var keyword : properties.keywords()) {
      // at times the cases are changed by Spring, e.g. when it tries to sanitize the input via a
      // qualified key; it might be upper case, lower case, etc., so anything with camel case, for
      // example, would not be matched. instead, we match all cases.
      if (key.toLowerCase().contains(keyword.toLowerCase())) {
        return data.withSanitizedValue();
      }
    }

    return data;
  }

  @ConfigurationProperties(prefix = "management.sanitize")
  public record ConfigSanitizingProperties(List<String> keywords) {}
}
