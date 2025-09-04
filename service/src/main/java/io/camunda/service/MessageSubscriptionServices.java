/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.authorization.Authorizations.MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION;

import io.camunda.search.clients.MessageSubscriptionSearchClient;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public class MessageSubscriptionServices
    extends SearchQueryService<
        MessageSubscriptionServices, MessageSubscriptionQuery, MessageSubscriptionEntity> {

  private final MessageSubscriptionSearchClient searchClient;

  public MessageSubscriptionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MessageSubscriptionSearchClient searchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.searchClient = searchClient;
  }

  @Override
  public SearchQueryResult<MessageSubscriptionEntity> search(final MessageSubscriptionQuery query) {
    return executeSearchRequest(
        () ->
            searchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION))
                .searchMessageSubscriptions(query));
  }

  @Override
  public MessageSubscriptionServices withAuthentication(
      final CamundaAuthentication authentication) {
    return new MessageSubscriptionServices(
        brokerClient, securityContextProvider, searchClient, authentication, executorProvider);
  }
}
