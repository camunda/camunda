/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.validation;

import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.service.exception.SecondaryStorageUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Validates that secondary storage is available for operations that require it.
 */
@Component
public class SecondaryStorageValidator {

  private final String databaseType;

  public SecondaryStorageValidator(@Value("${camunda.database.type:" + DatabaseConfig.ELASTICSEARCH + "}") final String databaseType) {
    this.databaseType = databaseType;
  }

  /**
   * Validates that secondary storage is enabled for the current deployment.
   * 
   * @throws SecondaryStorageUnavailableException if secondary storage is not available
   */
  public void validateSecondaryStorageEnabled() {
    final DatabaseType dbType = DatabaseType.from(databaseType);
    if (dbType.isNone()) {
      throw new SecondaryStorageUnavailableException();
    }
  }
}