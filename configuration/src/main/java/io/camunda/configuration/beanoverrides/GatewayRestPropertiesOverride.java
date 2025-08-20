/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.ProcessCache;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.GatewayRestProperties;
import io.camunda.configuration.beans.LegacyGatewayRestProperties;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.ProcessCacheConfiguration;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(LegacyGatewayRestProperties.class)
@DependsOn("unifiedConfigurationHelper")
public class GatewayRestPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;
  private final LegacyGatewayRestProperties legacyGatewayRestProperties;

  public GatewayRestPropertiesOverride(
      final UnifiedConfiguration unifiedConfiguration,
      final LegacyGatewayRestProperties legacyGatewayRestProperties) {
    this.unifiedConfiguration = unifiedConfiguration;
    this.legacyGatewayRestProperties = legacyGatewayRestProperties;
  }

  @Bean
  @Primary
  public GatewayRestProperties gatewayRestProperties() {
    final GatewayRestProperties override = new GatewayRestProperties();
    BeanUtils.copyProperties(legacyGatewayRestProperties, override);

    populateFromProcessCache(override);

    return override;
  }

  private void populateFromProcessCache(final GatewayRestProperties override) {
    final ProcessCache processCache =
        unifiedConfiguration.getCamunda().getApi().getRest().getProcessCache();
    final ProcessCacheConfiguration processCacheConfiguration = override.getProcessCache();
    processCacheConfiguration.setMaxSize(processCache.getMaxSize());
    processCacheConfiguration.setExpirationIdleMillis(processCache.getExpirationIdle().toMillis());
  }
}
