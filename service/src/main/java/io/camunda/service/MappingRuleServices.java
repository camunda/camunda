/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.MAPPING_RULE_READ_AUTHORIZATION;

import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryBase.AbstractQueryBuilder;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.value.MappingRuleType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class MappingRuleServices
    extends SearchQueryService<MappingRuleServices, MappingRuleQuery, MappingRuleEntity> {

  private final MappingRuleSearchClient mappingRuleSearchClient;

  public MappingRuleServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingRuleSearchClient mappingRuleSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
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
            .setMappingRuleId(request.mappingRuleId())
            .setType(request.type())
            .setOrigin(request.origin()),
        authentication);
  }

  public CompletableFuture<MappingRuleRecord> updateMappingRule(
      final MappingRuleDTO request, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerMappingRuleUpdateRequest()
            .setClaimName(request.claimName())
            .setClaimValue(request.claimValue())
            .setName(request.name())
            .setMappingRuleId(request.mappingRuleId())
            .setType(request.type())
            .setOrigin(request.origin()),
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
                        withRequiredAuthorization(MAPPING_RULE_READ_AUTHORIZATION, mappingRuleId)))
                .getMappingRule(mappingRuleId));
  }

  public CompletableFuture<MappingRuleRecord> deleteMappingRule(
      final String mappingRuleId, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerMappingRuleDeleteRequest().setMappingRuleId(mappingRuleId), authentication);
  }

  /**
   * Matches token-claim rules against the claims. User-attribute rules never match here, as there
   * are no user attributes to evaluate them against.
   */
  public Stream<MappingRuleEntity> getMatchingMappingRules(
      final Map<String, Object> claims, final CamundaAuthentication authentication) {
    return getMatchingMappingRules(claims, null, authentication);
  }

  /**
   * Matches token-claim rules against the token claims and user-attribute rules against the stored
   * attributes of the user. Token claims are never used for user-attribute rules.
   */
  public Stream<MappingRuleEntity> getMatchingMappingRules(
      final Map<String, Object> claims,
      final @Nullable Map<String, Object> userAttributes,
      final CamundaAuthentication authentication) {
    return getMatchingMappingRules(claims, userAttributes, null, authentication);
  }

  /**
   * Additionally matches group-attribute rules against the attributes of each group the user is a
   * direct member of; a rule matches if any group's attributes match. Token claims are never used
   * for group-attribute rules.
   *
   * <p>This overload knows no sources, so it only matches rules without an origin.
   */
  public Stream<MappingRuleEntity> getMatchingMappingRules(
      final Map<String, Object> claims,
      final @Nullable Map<String, Object> userAttributes,
      final @Nullable List<Map<String, Object>> groupAttributes,
      final CamundaAuthentication authentication) {
    return getMatchingMappingRules(
        claims,
        userAttributes,
        groupAttributes == null
            ? null
            : groupAttributes.stream()
                .map(attributes -> new SourcedAttributes("", attributes))
                .toList(),
        null,
        null,
        authentication);
  }

  /**
   * Like the overload without sources, but a rule with an origin only applies to principals of that
   * source:
   *
   * <ul>
   *   <li>token-claim rules to the logins of {@code loginSource}; a null or empty login source
   *       matches only rules without an origin;
   *   <li>user-attribute and group-attribute rules to the users owned by {@code userSource}; a null
   *       or empty user source matches only rules without an origin;
   *   <li>a user with a source only considers the groups of the same source.
   * </ul>
   */
  public Stream<MappingRuleEntity> getMatchingMappingRules(
      final Map<String, Object> claims,
      final @Nullable Map<String, Object> userAttributes,
      final @Nullable List<SourcedAttributes> groups,
      final @Nullable String loginSource,
      final @Nullable String userSource,
      final CamundaAuthentication authentication) {
    final var login = loginSource == null ? "" : loginSource;
    final var owner = userSource == null ? "" : userSource;
    final var rules =
        search(MappingRuleQuery.of(AbstractQueryBuilder::unlimited), authentication).items();
    final var tokenRules =
        rules.stream()
            .filter(
                r ->
                    !MappingRuleEntity.TYPE_USER_ATTRIBUTE.equals(r.type())
                        && !MappingRuleEntity.TYPE_GROUP_ATTRIBUTE.equals(r.type()))
            .filter(r -> appliesToSource(r, login));
    final var attributeRules =
        userAttributes == null || userAttributes.isEmpty()
            ? Stream.<MappingRuleEntity>empty()
            : rules.stream()
                .filter(r -> MappingRuleEntity.TYPE_USER_ATTRIBUTE.equals(r.type()))
                .filter(r -> appliesToSource(r, owner));
    final var groupAttributes =
        groups == null
            ? List.<Map<String, Object>>of()
            : groups.stream()
                .filter(group -> owner.isEmpty() || owner.equals(group.source()))
                .map(SourcedAttributes::attributes)
                .filter(attributes -> attributes != null && !attributes.isEmpty())
                .toList();
    final var groupRules =
        groupAttributes.isEmpty()
            ? List.<MappingRuleEntity>of()
            : rules.stream()
                .filter(r -> MappingRuleEntity.TYPE_GROUP_ATTRIBUTE.equals(r.type()))
                .filter(r -> appliesToSource(r, owner))
                .toList();
    final var groupMatches =
        groupAttributes.stream()
            .flatMap(
                attributes -> MappingRuleMatcher.matchingRules(groupRules.stream(), attributes));
    return Stream.of(
            MappingRuleMatcher.matchingRules(tokenRules, claims),
            MappingRuleMatcher.matchingRules(
                attributeRules, userAttributes == null ? Map.of() : userAttributes),
            groupMatches)
        .flatMap(s -> s)
        .distinct();
  }

  private static boolean appliesToSource(final MappingRuleEntity rule, final String source) {
    return rule.origin().isEmpty() || rule.origin().equals(source);
  }

  /** The attributes of a group together with the id of the source that owns it; empty if none. */
  public record SourcedAttributes(@Nullable String source, Map<String, Object> attributes) {}

  /** A null or missing {@code type} means {@link MappingRuleType#TOKEN_CLAIM}. */
  public record MappingRuleDTO(
      String claimName,
      String claimValue,
      String name,
      String mappingRuleId,
      MappingRuleType type,
      String origin) {

    public MappingRuleDTO(
        final String claimName,
        final String claimValue,
        final String name,
        final String mappingRuleId,
        final MappingRuleType type) {
      this(claimName, claimValue, name, mappingRuleId, type, "");
    }

    public MappingRuleDTO(
        final String claimName,
        final String claimValue,
        final String name,
        final String mappingRuleId) {
      this(claimName, claimValue, name, mappingRuleId, MappingRuleType.TOKEN_CLAIM, "");
    }
  }
}
