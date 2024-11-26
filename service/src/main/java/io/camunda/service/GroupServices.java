/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class GroupServices extends SearchQueryService<GroupServices, GroupQuery, GroupEntity> {

  private final GroupSearchClient groupSearchClient;

  public GroupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.groupSearchClient = groupSearchClient;
  }

  @Override
  public SearchQueryResult<GroupEntity> search(final GroupQuery query) {
    return groupSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.group().read())))
        .searchGroups(query);
  }

  @Override
  public GroupServices withAuthentication(final Authentication authentication) {
    return new GroupServices(
        brokerClient, securityContextProvider, groupSearchClient, authentication);
  }

  public CompletableFuture<GroupRecord> createGroup(final String name) {
    return sendBrokerRequest(new BrokerGroupCreateRequest().setName(name));
  }

  public GroupEntity getGroup(final Long groupKey) {
    return findGroup(groupKey)
        .orElseThrow(
            () -> new NotFoundException("Group with groupKey %d not found".formatted(groupKey)));
  }

  public Optional<GroupEntity> findGroup(final Long groupKey) {
    return search(SearchQueryBuilders.groupSearchQuery().filter(f -> f.groupKey(groupKey)).build())
        .items()
        .stream()
        .findFirst();
  }
}