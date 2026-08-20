/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import java.util.regex.Pattern;

public class ConfigurationValidator {

  private static final Pattern INVALID_INDEX_PREFIX_CHARS = Pattern.compile("[\\\\/*?\"<>| _]");

  public void validate(final ConfigurationService configurationService) {
    configurationService.getEmailAuthenticationConfiguration().validate();

    validateIndexPrefix(configurationService.getElasticSearchConfiguration().getIndexPrefix());
    validateIndexPrefix(configurationService.getOpenSearchConfiguration().getIndexPrefix());
  }

  private void validateIndexPrefix(final String indexPrefix) {
    if (indexPrefix == null) {
      return;
    }
    if (INVALID_INDEX_PREFIX_CHARS.matcher(indexPrefix).find()) {
      throw new OptimizeConfigurationException(
          "Optimize indexPrefix must not contain invalid characters [\\ / * ? \" < > | space _].");
    }
    if (indexPrefix.startsWith(".") || indexPrefix.startsWith("+")) {
      throw new OptimizeConfigurationException(
          "Optimize indexPrefix must not begin with invalid characters [. +].");
    }
  }
}
