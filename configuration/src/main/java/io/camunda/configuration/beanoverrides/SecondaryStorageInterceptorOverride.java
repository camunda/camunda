/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.cluster.SecondaryStorageReadiness;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.LegacySecondaryStorageInterceptor;
import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!restore")
@DependsOn("unifiedConfigurationHelper")
public class SecondaryStorageInterceptorOverride {

  @Bean
  @Primary
  public SecondaryStorageInterceptor secondaryStorageInterceptor(
      final UnifiedConfiguration uc,
      final LegacySecondaryStorageInterceptor legacySecondaryStorageInterceptor,
      final SecondaryStorageReadiness readiness) {
    final SecondaryStorageInterceptor override = new SecondaryStorageInterceptor(readiness);
    BeanUtils.copyProperties(legacySecondaryStorageInterceptor, override);
    override.setDatabaseType(uc.getCamunda().getData().getSecondaryStorage().getType().name());
    return override;
  }
}
