/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.shared;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.ClassLoadingExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryAllocationExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.ThreadExports;
import io.prometheus.client.hotspot.VersionInfoExports;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigureBefore(PrometheusMetricsExportAutoConfiguration.class)
public class PrometheusRegistryConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CollectorRegistry collectorRegistry() {
    final var registry = CollectorRegistry.defaultRegistry;

    (new MemoryPoolsExports()).register(registry);
    (new MemoryAllocationExports()).register(registry);
    (new BufferPoolsExports()).register(registry);
    (new GarbageCollectorExports()).register(registry);
    (new ThreadExports()).register(registry);
    (new ClassLoadingExports()).register(registry);
    (new VersionInfoExports()).register(registry);

    return registry;
  }
}
