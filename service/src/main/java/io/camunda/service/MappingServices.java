/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.MappingSearchClient;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class MappingServices
    extends SearchQueryService<MappingServices, MappingQuery, MappingEntity> {

  private final MappingSearchClient mappingSearchClient;

  public MappingServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingSearchClient mappingSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.mappingSearchClient = mappingSearchClient;
  }

  @Override
  public SearchQueryResult<MappingEntity> search(final MappingQuery query) {
    return mappingSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.mapping().read())))
        .searchMappings(query);
  }

  @Override
  public MappingServices withAuthentication(final Authentication authentication) {
    return new MappingServices(
        brokerClient, securityContextProvider, mappingSearchClient, authentication);
  }

  public CompletableFuture<MappingRecord> createMapping(final MappingDTO request) {
    return sendBrokerRequest(
        new BrokerMappingCreateRequest()
            .setMappingKey(request.mappingKey())
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue()));
  }

  public MappingEntity getMapping(final Long mappingKey) {
    return findMapping(mappingKey)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Mapping with mappingKey %d not found".formatted(mappingKey)));
  }

  public Optional<MappingEntity> findMapping(final Long mappingKey) {
    return search(
            SearchQueryBuilders.mappingSearchQuery().filter(f -> f.mappingKey(mappingKey)).build())
        .items()
        .stream()
        .findFirst();
  }

  public record MappingDTO(Long mappingKey, String claimName, String claimValue) {}
}
