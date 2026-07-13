/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;

public class EngineValidators {
  private static final String PREFIX = "camunda.processing.engine.validators";

  private static final Set<String> LEGACY_RESULTS_OUTPUT_MAX_SIZE_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.validators.resultsOutputMaxSize");

  /**
   * Configures the maximum size, in bytes, of the validation error output produced when deploying
   * an invalid BPMN or DMN resource.
   */
  private int resultsOutputMaxSize = DEFAULT_VALIDATORS_RESULTS_OUTPUT_MAX_SIZE;

  public int getResultsOutputMaxSize() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".results-output-max-size",
        resultsOutputMaxSize,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_RESULTS_OUTPUT_MAX_SIZE_PROPERTIES);
  }

  public void setResultsOutputMaxSize(final int resultsOutputMaxSize) {
    this.resultsOutputMaxSize = resultsOutputMaxSize;
  }
}
