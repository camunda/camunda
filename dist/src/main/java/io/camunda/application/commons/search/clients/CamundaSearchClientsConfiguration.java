/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search.clients;

import io.camunda.application.commons.search.condition.ConditionalOnDatabaseDisabled;
import io.camunda.application.commons.search.condition.ConditionalOnDatabaseEnabled;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.impl.NoopSearchClientsProxy;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.security.ResourceAccessController;
import io.camunda.security.impl.CompositeResourceAccessController;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class CamundaSearchClientsConfiguration {

  @Bean
  @ConditionalOnDatabaseEnabled
  public CamundaSearchClients searchClients(
      final SearchClientReaders searchClientReaders,
      final List<ResourceAccessController> resourceAccessControllers) {
    return new CamundaSearchClients(
        searchClientReaders, new CompositeResourceAccessController(resourceAccessControllers));
  }

  @Bean
  @ConditionalOnDatabaseDisabled
  public NoopSearchClientsProxy noopSearchClientsProxy() {
    return new NoopSearchClientsProxy();
  }
}
