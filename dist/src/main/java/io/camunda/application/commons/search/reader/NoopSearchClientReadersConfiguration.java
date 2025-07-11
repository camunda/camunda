/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search.reader;

import io.camunda.application.commons.search.condition.ConditionalOnDatabaseDisabled;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnDatabaseDisabled
public class NoopSearchClientReadersConfiguration {

  @Bean
  public AuthorizationReader authorizationReader() {
    return new NoopAuthorizationReader();
  }

  static class NoopAuthorizationReader implements AuthorizationReader {
    @Override
    public AuthorizationEntity getByKey(
        final String key, final ResourceAccessChecks resourceAccessChecks) {
      return null;
    }

    @Override
    public SearchQueryResult<AuthorizationEntity> search(
        final AuthorizationQuery query, final ResourceAccessChecks resourceAccessChecks) {
      return SearchQueryResult.empty();
    }
  }
}
