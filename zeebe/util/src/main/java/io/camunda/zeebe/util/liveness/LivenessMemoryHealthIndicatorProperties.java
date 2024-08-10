/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.liveness;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.health.liveness.memory")
public class LivenessMemoryHealthIndicatorProperties
    extends io.camunda.zeebe.util.health.MemoryHealthIndicatorProperties {

  @Override
  protected double getDefaultThreshold() {
    return 0.01;
  }
}
