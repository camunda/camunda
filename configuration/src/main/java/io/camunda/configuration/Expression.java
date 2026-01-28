/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.zeebe.engine.EngineConfiguration;
import java.time.Duration;
import java.util.Set;

/**
 * Configuration properties for the concept of Expressions.
 *
 * @see <a href="https://docs.camunda.io/docs/components/concepts/expressions/">Documentation</a>
 */
public class Expression {

  private static final String PREFIX = "camunda.expression";

  private static final Set<String> LEGACY_TIMEOUT_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.expression.timeout");

  /**
   * The timeout for expression evaluation. If an expression takes longer than this timeout to
   * evaluate, it will be interrupted and an incident will be raised. Setting a lower value avoids
   * the expression evaluation blocking the execution of other process instances on the same
   * partition for too long. We recommend keeping this below 5 seconds to avoid unhealthy partitions
   * due to 'actor appears blocked'. Defaults to {@link
   * EngineConfiguration#DEFAULT_EXPRESSION_EVALUATION_TIMEOUT}.
   */
  private Duration timeout = EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  public Duration getTimeout() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".timeout",
        timeout,
        Duration.class,
        UnifiedConfigurationHelper.BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_TIMEOUT_PROPERTIES);
  }

  public void setTimeout(final Duration timeout) {
    this.timeout = timeout;
  }
}
