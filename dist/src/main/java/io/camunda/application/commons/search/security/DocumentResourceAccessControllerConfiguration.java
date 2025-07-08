/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search.security;

import io.camunda.application.commons.search.condition.SearchEngineEnabledCondition;
import io.camunda.search.clients.security.DocumentResourceAccessController;
import io.camunda.search.clients.security.ResourceAccessProvider;
import io.camunda.search.clients.security.TenantAccessProvider;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@Conditional(SearchEngineEnabledCondition.class)
public class DocumentResourceAccessControllerConfiguration {

  @Bean
  public DocumentResourceAccessController documentResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    return new DocumentResourceAccessController(resourceAccessProvider, tenantAccessProvider);
  }
}
