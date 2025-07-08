/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search.security;

import io.camunda.application.commons.search.condition.ConditionalOnDatabaseEnabled;
import io.camunda.search.clients.security.ResourceAccessController;
import io.camunda.search.clients.security.ResourceAccessProvider;
import io.camunda.search.clients.security.TenantAccessProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.impl.AnonymousResourceAccessController;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.impl.DefaultResourceAccessProvider;
import io.camunda.security.impl.DefaultTenantAccessProvider;
import io.camunda.security.impl.DisabledResourceAccessProvider;
import io.camunda.security.impl.DisabledTenantAccessProvider;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnDatabaseEnabled
public class DefaultResourceAccessControllerConfiguration {

  @Bean
  public ResourceAccessController anonymousResourceAccessController() {
    return new AnonymousResourceAccessController();
  }

  @Bean
  public ResourceAccessProvider authorizationCheckController(
      final AuthorizationChecker authorizationChecker,
      final SecurityConfiguration securityConfiguration) {
    if (securityConfiguration.getAuthorizations().isEnabled()) {
      return new DefaultResourceAccessProvider(authorizationChecker);
    } else {
      return new DisabledResourceAccessProvider();
    }
  }

  @Bean
  public TenantAccessProvider tenantCheckController(
      final SecurityConfiguration securityConfiguration) {
    if (securityConfiguration.getMultiTenancy().isEnabled()) {
      return new DefaultTenantAccessProvider();
    } else {
      return new DisabledTenantAccessProvider();
    }
  }
}
