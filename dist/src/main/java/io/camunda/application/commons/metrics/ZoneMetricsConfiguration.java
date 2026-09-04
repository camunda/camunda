/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.metrics;

import io.camunda.configuration.Camunda;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ZoneMetricsConfiguration {

  private static final String ZONE_TAG = "zone";

  @Bean
  public MeterRegistryCustomizer<MeterRegistry> zoneMeterRegistryCustomizer(final Camunda camunda) {
    final var zone = camunda.getCluster().getZone();
    return registry -> {
      if (zone != null) {
        registry.config().commonTags(ZONE_TAG, zone);
      }
    };
  }
}
