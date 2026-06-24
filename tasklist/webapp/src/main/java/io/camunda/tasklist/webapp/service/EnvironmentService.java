/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.spring.utils.DatabaseTypeUtils;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentService.class);

  private final Environment environment;
  private final Map<String, String> databaseTypes =
      Map.of(
          "elasticsearch", "document-store",
          "opensearch", "document-store",
          "rdbms", "rdbms",
          "none", "none");

  public EnvironmentService(final Environment environment) {
    this.environment = environment;
  }

  public String getDatabaseType() {
    final var dbType = DatabaseTypeUtils.getDatabaseTypeOrDefault(environment);
    if (!databaseTypes.containsKey(dbType)) {
      LOGGER.warn("Could not identify database type: {}", dbType);
      return "unknown";
    }
    return databaseTypes.get(dbType);
  }
}
