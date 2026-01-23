/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.management;

import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("searchEngineCheck")
@ConditionalOnRdbmsDisabled
public class SearchEngineHealthIndicator implements HealthIndicator {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineHealthIndicator.class);

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @Override
  public Health health() {
    LOGGER.debug("Search engine check is called");
    if (!indexSchemaValidator.isHealthCheckEnabled() || indexSchemaValidator.schemaExists()) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }
}
