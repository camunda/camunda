/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.MAPPING_RULE_READ_AUTHORIZATION;

import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class MappingRuleServices
    extends SearchQueryService<MappingRuleServices, MappingRuleQuery, MappingRuleEntity> {

  private final MappingRuleSearchClient mappingRuleSearchClient;

  public MappingRuleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingRuleSearchClient mappingRuleSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.mappingRuleSearchClient = mappingRuleSearchClient;
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> search(
      final MappingRuleQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            mappingRuleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, MAPPING_RULE_READ_AUTHORIZATION))
                .searchMappingRules(query));
  }

  public CompletableFuture<MappingRuleRecord> createMappingRule(
      final MappingRuleDTO request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerMappingRuleCreateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingRuleId(request.mappingRuleId()),
        authentication);
  }

  public CompletableFuture<MappingRuleRecord> updateMappingRule(
      final MappingRuleDTO request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerMappingRuleUpdateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingRuleId(request.mappingRuleId()),
        authentication);
  }

  public MappingRuleEntity getMappingRule(
      final String mappingRuleId, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            mappingRuleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(MAPPING_RULE_READ_AUTHORIZATION, mappingRuleId)))
                .getMappingRule(mappingRuleId));
  }

  public CompletableFuture<MappingRuleRecord> deleteMappingRule(
      final String mappingRuleId, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerMappingRuleDeleteRequest().setMappingRuleId(mappingRuleId), authentication);
  }

  public Stream<MappingRuleEntity> getMatchingMappingRules(
      final Map<String, Object> claims, final CamundaAuthentication authentication) {
    if (claims == null || claims.isEmpty()) {
      return Stream.empty();
    }
    final var claimPaths = extractClaimPaths(claims);
    if (claimPaths.isEmpty()) {
      return Stream.empty();
    }
    return MappingRuleMatcher.matchingRules(
        search(
                MappingRuleQuery.of(q -> q.filter(f -> f.claimNames(claimPaths)).unlimited()),
                authentication)
            .items()
            .stream(),
        claims);
  }

  private static List<String> extractClaimPaths(final Map<String, Object> claims) {
    final List<String> paths = new ArrayList<>();
    for (final var entry : claims.entrySet()) {
      final String path = "$." + entry.getKey();
      paths.add(path);
      addNestedPaths(path, entry.getValue(), paths);
    }
    return paths;
  }

  private static void addNestedPaths(
      final String prefix, final Object value, final List<String> paths) {
    if (value instanceof final Map<?, ?> map) {
      for (final var entry : map.entrySet()) {
        final String path = prefix + "." + entry.getKey();
        paths.add(path);
        addNestedPaths(path, entry.getValue(), paths);
      }
    }
  }

  public record MappingRuleDTO(
      String claimName, String claimValue, String name, String mappingRuleId) {}
}
