/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** {@link EnableAutoConfiguration Auto-configuration} for {@link MemoryHealthIndicator}. */
@Singleton
@Requires(property = "management.health.memory.enabled", value = "true")
public class MemoryHealthIndicatorAutoConfiguration {

  @Inject
  @Singleton
  @Replaces(named = "memoryHealthIndicator")
  @Requires("memoryHealthIndicator")
  public MemoryHealthIndicator memoryHealthIndicator(
      final MemoryHealthIndicatorProperties properties) {
    return new MemoryHealthIndicator(properties.getThreshold());
  }
}
