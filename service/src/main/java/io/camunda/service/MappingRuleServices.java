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
import io.camunda.search.query.SearchQueryBase.AbstractQueryBuilder;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.MappingRuleMatcher;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
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
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.mappingRuleSearchClient = mappingRuleSearchClient;
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> search(final MappingRuleQuery query) {
    final var securityContext =
        securityContextProvider.provideSecurityContext(
            authentication, MAPPING_RULE_READ_AUTHORIZATION);

    return executeSearchRequest(
        () ->
            mappingRuleSearchClient.withSecurityContext(securityContext).searchMappingRules(query));
  }

  @Override
  public MappingRuleServices withAuthentication(final CamundaAuthentication authentication) {
    return new MappingRuleServices(
        brokerClient, securityContextProvider, mappingRuleSearchClient, authentication);
  }

  public CompletableFuture<MappingRuleRecord> createMappingRule(final MappingRuleDTO request) {
    return sendBrokerRequest(
        new BrokerMappingRuleCreateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingRuleId(request.mappingRuleId()));
  }

  public CompletableFuture<MappingRuleRecord> updateMappingRule(final MappingRuleDTO request) {
    return sendBrokerRequest(
        new BrokerMappingRuleUpdateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingRuleId(request.mappingRuleId()));
  }

  public MappingRuleEntity getMappingRule(final String mappingRuleId) {
    return executeSearchRequest(
        () ->
            mappingRuleSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(MAPPING_RULE_READ_AUTHORIZATION, mappingRuleId)))
                .getMappingRule(mappingRuleId));
  }

  public CompletableFuture<MappingRuleRecord> deleteMappingRule(final String mappingRuleId) {
    return sendBrokerRequest(new BrokerMappingRuleDeleteRequest().setMappingRuleId(mappingRuleId));
  }

  public Stream<MappingRuleEntity> getMatchingMappingRules(final Map<String, Object> claims) {
    return MappingRuleMatcher.matchingRules(
        search(MappingRuleQuery.of(AbstractQueryBuilder::unlimited)).items().stream(), claims);
  }

  public record MappingRuleDTO(
      String claimName, String claimValue, String name, String mappingRuleId) {}
}
