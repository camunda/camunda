/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.management;

import io.camunda.operate.property.OperateProperties;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("indicesCheck")
@ConditionalOnRdbmsDisabled
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
  public Health health() {
    LOGGER.debug("Indices check is called");
    if (indicesCheck.isHealthy() && indicesCheck.indicesArePresent()) {
      return Health.up().build();
    } else {
      return Health.down().build();
    }
  }
}
