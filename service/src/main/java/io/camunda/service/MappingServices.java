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
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.MappingFilter.Claim;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ExceptionUtil;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingDeleteRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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

  public List<MappingEntity> findAll(final MappingQuery query) {
    return mappingSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.mapping().read())))
        .findAllMappings(query);
  }

  @Override
  public MappingServices withAuthentication(final Authentication authentication) {
    return new MappingServices(
        brokerClient, securityContextProvider, mappingSearchClient, authentication);
  }

  public CompletableFuture<MappingRecord> createMapping(final MappingDTO request) {
    return sendBrokerRequest(
        new BrokerMappingCreateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setId(request.id()));
  }

  public MappingEntity getMapping(final Long mappingKey) {
    return findMapping(mappingKey)
        .orElseThrow(
            () ->
                new CamundaSearchException(
                    ExceptionUtil.ERROR_NOT_FOUND_MAPPING_BY_KEY.formatted(mappingKey),
                    CamundaSearchException.Reason.NOT_FOUND));
  }

  public Optional<MappingEntity> findMapping(final Long mappingKey) {
    return search(
            SearchQueryBuilders.mappingSearchQuery().filter(f -> f.mappingKey(mappingKey)).build())
        .items()
        .stream()
        .findFirst();
  }

  public Optional<MappingEntity> findMapping(final MappingDTO request) {
    return search(
            SearchQueryBuilders.mappingSearchQuery()
                .filter(f -> f.claimName(request.claimName()).claimValue(request.claimValue()))
                .page(p -> p.size(1))
                .build())
        .items()
        .stream()
        .findFirst();
  }

  public CompletableFuture<MappingRecord> deleteMapping(final long mappingKey) {
    return sendBrokerRequest(new BrokerMappingDeleteRequest().setMappingKey(mappingKey));
  }

  public List<MappingEntity> getMatchingMappings(final Map<String, Object> claims) {
    final List<Claim> claimFilters =
        claims.entrySet().stream()
            .flatMap(
                claimEntry ->
                    flattenClaimValue(claimEntry.getValue())
                        .map(value -> new Claim(claimEntry.getKey(), value)))
            .toList();
    return findAll(MappingQuery.of(q -> q.filter(f -> f.claims(claimFilters))));
  }

  private static Stream<String> flattenClaimValue(final Object value) {
    if (value == null) {
      return Stream.of();
    }
    if (value instanceof final Collection<?> collection) {
      return collection.stream().flatMap(MappingServices::flattenClaimValue);
    }
    return Stream.of(String.valueOf(value));
  }

  public record MappingDTO(String claimName, String claimValue, String name, String id) {}
}
