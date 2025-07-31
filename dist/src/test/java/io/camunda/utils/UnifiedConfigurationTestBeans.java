/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.utils;

import io.camunda.configuration.UnifiedConfigurationHelper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@TestConfiguration
public class UnifiedConfigurationTestBeans {
  @Bean
  public UnifiedConfigurationHelper unifiedConfigurationHelper(final Environment environment) {
    return new UnifiedConfigurationHelper(environment);
  }
}
