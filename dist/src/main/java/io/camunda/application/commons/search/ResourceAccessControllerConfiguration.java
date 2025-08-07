/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.condition.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.read.security.RdbmsResourceAccessController;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.clients.auth.DefaultResourceAccessProvider;
import io.camunda.search.clients.auth.DefaultTenantAccessProvider;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.search.clients.auth.DisabledTenantAccessProvider;
import io.camunda.search.clients.auth.DocumentBasedResourceAccessController;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccessProvider;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnSecondaryStorageEnabled
public class ResourceAccessControllerConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.authorizations",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ResourceAccessProvider resourceAccessProvider(final AuthorizationChecker checker) {
    return new DefaultResourceAccessProvider(checker);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.authorizations",
      name = "enabled",
      havingValue = "false")
  public ResourceAccessProvider disabledResourceAccessProvider() {
    return new DisabledResourceAccessProvider();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.multiTenancy",
      name = "checksEnabled",
      havingValue = "true")
  public TenantAccessProvider tenantAccessProvider() {
    return new DefaultTenantAccessProvider();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.multiTenancy",
      name = "checksEnabled",
      havingValue = "false",
      matchIfMissing = true)
  public TenantAccessProvider disabledTenantAccessProvider() {
    return new DisabledTenantAccessProvider();
  }

  @Bean
  public ResourceAccessController anonymousResourceAccessController() {
    return new AnonymousResourceAccessController();
  }

  @Bean
  @ConditionalOnSecondaryStorageType({DatabaseConfig.ELASTICSEARCH, DatabaseConfig.OPENSEARCH})
  public ResourceAccessController documentBasedResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    return new DocumentBasedResourceAccessController(resourceAccessProvider, tenantAccessProvider);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(DatabaseConfig.RDBMS)
  public ResourceAccessController rdbmsResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    return new RdbmsResourceAccessController(resourceAccessProvider, tenantAccessProvider);
  }
}
