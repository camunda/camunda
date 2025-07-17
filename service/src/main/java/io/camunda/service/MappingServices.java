/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class MappingServices
    extends SearchQueryService<MappingServices, MappingRuleQuery, MappingRuleEntity> {

  private final MappingRuleSearchClient mappingRuleSearchClient;

  public MappingServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingRuleSearchClient mappingRuleSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.mappingRuleSearchClient = mappingRuleSearchClient;
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> search(final MappingRuleQuery query) {
    return executeSearchRequest(
        () ->
            mappingRuleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, Authorization.of(a -> a.mappingRule().read())))
                .searchMappingRules(query));
  }

  @Override
  public MappingServices withAuthentication(final CamundaAuthentication authentication) {
    return new MappingServices(
        brokerClient, securityContextProvider, mappingRuleSearchClient, authentication);
  }

  public CompletableFuture<MappingRecord> createMapping(final MappingDTO request) {
    return sendBrokerRequest(
        new BrokerMappingCreateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingId(request.mappingId()));
  }

  public CompletableFuture<MappingRecord> updateMapping(final MappingDTO request) {
    return sendBrokerRequest(
        new BrokerMappingUpdateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingId(request.mappingId()));
  }

  public MappingRuleEntity getMapping(final String mappingId) {
    return search(
            SearchQueryBuilders.mappingSearchQuery()
                .filter(f -> f.mappingRuleId(mappingId))
                .singleResult()
                .build())
        .items()
        .getFirst();
  }

  public Optional<MappingRuleEntity> findMapping(final MappingDTO request) {
    return search(
            SearchQueryBuilders.mappingSearchQuery()
                .filter(f -> f.claimName(request.claimName()).claimValue(request.claimValue()))
                .page(p -> p.size(1))
                .build())
        .items()
        .stream()
        .findFirst();
  }

  public CompletableFuture<MappingRecord> deleteMapping(final String mappingId) {
    return sendBrokerRequest(new BrokerMappingDeleteRequest().setMappingId(mappingId));
  }

  public Stream<MappingRuleEntity> getMatchingMappings(final Map<String, Object> claims) {
    return MappingRuleMatcher.matchingRules(
        search(MappingRuleQuery.of(q -> q.unlimited())).items().stream(), claims);
  }

  public record MappingDTO(String claimName, String claimValue, String name, String mappingId) {}
}
