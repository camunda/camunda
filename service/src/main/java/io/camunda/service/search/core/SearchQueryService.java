/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.core;

import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.ApiServices;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public abstract class SearchQueryService<T extends ApiServices<T>, Q extends SearchQueryBase, D>
    extends ApiServices<T> {

  protected SearchQueryService(
      final BrokerClient brokerClient,
      final SecurityConfiguration securityConfiguration,
      final Authentication authentication) {
    super(brokerClient, securityConfiguration, authentication);
  }

  public abstract SearchQueryResult<D> search(final Q query);
}
