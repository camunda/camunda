/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.configuration;

import io.camunda.search.connect.configuration.DatabaseConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class that prevents Optimize from starting when database type is 'none'.
 * This ensures consistent behavior across the Camunda platform by failing fast with a clear error message.
 */
@Configuration
@ConditionalOnProperty(
    prefix = "camunda.database",
    name = "type",
    havingValue = DatabaseConfig.NONE)
public class OptimizeDatabaseConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(OptimizeDatabaseConfiguration.class);

  @PostConstruct
  public void checkDatabaseConfiguration() {
    final String errorMessage = "Optimize is not supported without secondary storage. "
        + "The database type is configured as 'none', but Optimize requires a secondary storage "
        + "backend to function properly. "
        + "Please configure 'camunda.database.type' to a valid secondary storage type, "
        + "or remove Optimize from your deployment when running in no-secondary-storage mode.";
    
    LOGGER.error(errorMessage);
    throw new IllegalStateException(errorMessage);
  }
}