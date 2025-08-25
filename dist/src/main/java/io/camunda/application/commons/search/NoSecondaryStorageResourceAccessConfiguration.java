/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for ResourceAccessProvider when secondary storage is disabled. Provides a disabled
 * implementation that grants all access since authorization checks require database storage which
 * is not available in no-secondary-storage scenarios.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnSecondaryStorageDisabled
public class NoSecondaryStorageResourceAccessConfiguration {

  /**
   * Provides a ResourceAccessProvider that allows all access when secondary storage is disabled.
   * This is necessary because the default ResourceAccessProvider requires database access for
   * authorization checks, which is not available in no-secondary-storage scenarios.
   *
   * @return DisabledResourceAccessProvider that grants all access
   */
  @Bean
  @ConditionalOnMissingBean(ResourceAccessProvider.class)
  public ResourceAccessProvider noSecondaryStorageResourceAccessProvider() {
    return new DisabledResourceAccessProvider();
  }
}
