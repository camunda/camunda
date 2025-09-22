/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.Executor;
import io.camunda.configuration.ProcessCache;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.GatewayRestProperties;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.ApiExecutorConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.ProcessCacheConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class GatewayRestPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;

  public GatewayRestPropertiesOverride(final UnifiedConfiguration unifiedConfiguration) {
    this.unifiedConfiguration = unifiedConfiguration;
  }

  @Bean
  @Primary
  public GatewayRestProperties gatewayRestProperties() {
    final GatewayRestProperties override = new GatewayRestProperties();

    populateFromProcessCache(override);
    populateFromExecutor(override);

    return override;
  }

  private void populateFromProcessCache(final GatewayRestProperties override) {
    final ProcessCache processCache =
        unifiedConfiguration.getCamunda().getApi().getRest().getProcessCache();
    final ProcessCacheConfiguration processCacheConfiguration = override.getProcessCache();
    processCacheConfiguration.setMaxSize(processCache.getMaxSize());
    processCacheConfiguration.setExpirationIdleMillis(processCache.getExpirationIdle().toMillis());
  }

  private void populateFromExecutor(final GatewayRestProperties override) {
    final Executor executor = unifiedConfiguration.getCamunda().getApi().getRest().getExecutor();
    final ApiExecutorConfiguration apiExecutorConfiguration = override.getApiExecutor();
    apiExecutorConfiguration.setCorePoolSizeMultiplier(executor.getCorePoolSizeMultiplier());
    apiExecutorConfiguration.setMaxPoolSizeMultiplier(executor.getMaxPoolSizeMultiplier());
    apiExecutorConfiguration.setKeepAliveSeconds(executor.getKeepAlive().getSeconds());
    apiExecutorConfiguration.setQueueCapacity(executor.getQueueCapacity());
  }
}
