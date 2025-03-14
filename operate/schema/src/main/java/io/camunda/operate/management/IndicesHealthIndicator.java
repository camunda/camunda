/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.management;

import io.camunda.config.operate.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("indicesCheck")
public class IndicesHealthIndicator implements HealthIndicator {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndicesHealthIndicator.class);

  private final IndicesCheck indicesCheck;
  private final OperateProperties properties;

  public IndicesHealthIndicator(
      final IndicesCheck indicesCheck, final OperateProperties operateProperties) {
    this.indicesCheck = indicesCheck;
    properties = operateProperties;
  }

  @Override
  public Health getHealth(final boolean includeDetails) {
    return health();
  }

  @Override
  public Health health() {
    LOGGER.debug("Indices check is called");
    if (indicesCheck.isHealthy() && indicesCheck.indicesArePresent()) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }
}
