/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.MappingRuleType;
import io.camunda.zeebe.util.cache.CaffeineCacheStatsCounter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

@NullMarked
public final class MembershipStateAdapter implements MembershipPort {

  private static final Logger LOG = Loggers.ENGINE_IDENTITY_LOGGER;

  private final MappingRuleState mappingRuleState;
  private final MembershipState membershipState;
  private final UserState userState;
  private final GroupState groupState;
  private final LoadingCache<MembershipCacheKey, List<String>> membershipCache;

  public MembershipStateAdapter(
      final MappingRuleState mappingRuleState,
      final MembershipState membershipState,
      final UserState userState,
      final GroupState groupState,
      final EngineConfiguration config,
      final MeterRegistry meterRegistry) {
    this.mappingRuleState = mappingRuleState;
    this.membershipState = membershipState;
    this.userState = userState;
    this.groupState = groupState;
    final var statsCounter =
        new CaffeineCacheStatsCounter(
            AuthorizationCacheMetrics.NAMESPACE, "membership", meterRegistry);
    membershipCache =
        Caffeine.newBuilder()
            .expireAfterWrite(config.getAuthorizationsCacheTtl())
            .maximumSize(config.getAuthorizationsCacheCapacity())
            .recordStats(() -> statsCounter)
            .build(new MembershipCacheLoader(membershipState));
  }

  /** Invalidates all cached memberships, forcing fresh loads on next access. */
  public void invalidateAll() {
    membershipCache.invalidateAll();
  }

  /**
   * Returns mapping rule IDs whose conditions match: {@link MappingRuleType#TOKEN_CLAIM} rules
   * against the token claims, {@link MappingRuleType#USER_ATTRIBUTE} rules against the attributes
   * stored with the user. Token claims are never matched by attribute rules, so a token cannot
   * claim an attribute it does not have in the state.
   */
  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    LOG.trace("Resolving mapping rule IDs for principal {}", query.principalId());
    final var rawClaims = query.tokenClaims().get(Authorization.USER_TOKEN_CLAIMS);
    @SuppressWarnings("unchecked")
    final Map<String, Object> tokenClaims =
        rawClaims instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    final var user = persistedUser(query);
    final var userSource = user.map(u -> u.getUser().getSource()).orElse("");
    final var rawLoginSource = query.tokenClaims().get(Authorization.LOGIN_SOURCE);
    final var loginSource = rawLoginSource instanceof String source ? source : "";
    final var rules = mappingRuleState.getAll();
    final var tokenRules =
        rules.stream()
            .filter(
                r ->
                    r.getType() != MappingRuleType.USER_ATTRIBUTE
                        && r.getType() != MappingRuleType.GROUP_ATTRIBUTE)
            .filter(r -> appliesToSource(r, loginSource));
    final Map<String, Object> attributes =
        user.map(u -> u.getUser().getAttributes()).orElse(Map.of());
    final var attributeRules =
        attributes.isEmpty()
            ? Stream.<PersistedMappingRule>empty()
            : rules.stream()
                .filter(r -> r.getType() == MappingRuleType.USER_ATTRIBUTE)
                .filter(r -> appliesToSource(r, userSource));
    // Direct group memberships only: mapping rules are resolved before groups, so groups derived
    // from mapping rules would be circular.
    final var groupAttributes = groupAttributes(query, userSource);
    final var groupRules =
        groupAttributes.isEmpty()
            ? List.<PersistedMappingRule>of()
            : rules.stream()
                .filter(r -> r.getType() == MappingRuleType.GROUP_ATTRIBUTE)
                .filter(r -> appliesToSource(r, userSource))
                .toList();
    final var groupMatches =
        groupAttributes.stream()
            .flatMap(attrs -> MappingRuleMatcher.matchingRules(groupRules.stream(), attrs));
    return Stream.of(
            MappingRuleMatcher.matchingRules(tokenRules, tokenClaims),
            MappingRuleMatcher.matchingRules(attributeRules, attributes),
            groupMatches)
        .flatMap(s -> s)
        .map(PersistedMappingRule::getMappingRuleId)
        .distinct()
        .toList();
  }

  /** A rule without origin applies to every source; otherwise the origin must equal the source. */
  private static boolean appliesToSource(final PersistedMappingRule rule, final String source) {
    final var origin = rule.getOrigin();
    return origin.isEmpty() || origin.equals(source);
  }

  /** The stored user of the user principal; empty for clients and unknown users. */
  private Optional<PersistedUser> persistedUser(final MembershipQuery query) {
    if (query.principalType() != PrincipalType.USER) {
      return Optional.empty();
    }
    return userState.getUser(query.principalId());
  }

  /**
   * The non-empty attribute maps of the groups the user principal is a direct member of; empty for
   * clients. A user owned by a source only considers the groups of the same source.
   */
  private List<Map<String, Object>> groupAttributes(
      final MembershipQuery query, final String userSource) {
    if (query.principalType() != PrincipalType.USER) {
      return List.of();
    }
    return getMemberships(EntityType.USER, query.principalId(), RelationType.GROUP).stream()
        .map(groupState::get)
        .flatMap(Optional::stream)
        .filter(group -> userSource.isEmpty() || group.getSource().equals(userSource))
        .map(group -> group.getAttributes())
        .filter(attrs -> !attrs.isEmpty())
        .toList();
  }

  /**
   * Returns group IDs for the principal, from token claims or membership state plus mapping rules.
   */
  @Override
  public List<String> groupIds(final MembershipQuery query) {
    LOG.trace("Resolving group IDs for principal {}", query.principalId());
    final var rawGroups = query.tokenClaims().get(Authorization.USER_GROUPS_CLAIMS);
    final var result = new LinkedHashSet<String>();
    if (rawGroups instanceof List<?> groupsClaims) {
      // OIDC mode: groups come solely from the token claim. This matches main's
      // ClaimsExtractor.getGroups, which ignores mapping-rule group memberships when a groups claim
      // is present.
      groupsClaims.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .forEach(result::add);
    } else {
      getMemberships(toEntityType(query.principalType()), query.principalId(), RelationType.GROUP)
          .forEach(result::add);
      query
          .resolvedMappingRuleIds()
          .forEach(
              ruleId ->
                  getMemberships(EntityType.MAPPING_RULE, ruleId, RelationType.GROUP)
                      .forEach(result::add));
    }
    return new ArrayList<>(result);
  }

  /** Returns role IDs for the principal and all resolved groups. */
  @Override
  public List<String> roleIds(final MembershipQuery query) {
    LOG.trace("Resolving role IDs for principal {}", query.principalId());
    final var result = new LinkedHashSet<String>();
    getMemberships(toEntityType(query.principalType()), query.principalId(), RelationType.ROLE)
        .forEach(result::add);
    query
        .resolvedGroupIds()
        .forEach(
            groupId ->
                getMemberships(EntityType.GROUP, groupId, RelationType.ROLE).forEach(result::add));
    query
        .resolvedMappingRuleIds()
        .forEach(
            ruleId ->
                getMemberships(EntityType.MAPPING_RULE, ruleId, RelationType.ROLE)
                    .forEach(result::add));
    return new ArrayList<>(result);
  }

  /** Returns tenant IDs via all inheritance paths: direct, roles, groups, and group roles. */
  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    LOG.trace("Resolving tenant IDs for principal {}", query.principalId());
    final var result = new LinkedHashSet<String>();
    final var entityType = toEntityType(query.principalType());
    getMemberships(entityType, query.principalId(), RelationType.TENANT).forEach(result::add);
    query
        .resolvedRoleIds()
        .forEach(
            roleId ->
                getMemberships(EntityType.ROLE, roleId, RelationType.TENANT).forEach(result::add));
    query
        .resolvedGroupIds()
        .forEach(
            groupId -> {
              getMemberships(EntityType.GROUP, groupId, RelationType.TENANT).forEach(result::add);
              getMemberships(EntityType.GROUP, groupId, RelationType.ROLE).stream()
                  .flatMap(
                      roleId ->
                          getMemberships(EntityType.ROLE, roleId, RelationType.TENANT).stream())
                  .forEach(result::add);
            });
    query
        .resolvedMappingRuleIds()
        .forEach(
            mrId ->
                getMemberships(EntityType.MAPPING_RULE, mrId, RelationType.TENANT)
                    .forEach(result::add));
    return new ArrayList<>(result);
  }

  /** Returns cached memberships, loading from state on cache miss. */
  private List<String> getMemberships(
      final EntityType entityType, final String entityId, final RelationType relationType) {
    return membershipCache.get(new MembershipCacheKey(entityType, entityId, relationType));
  }

  /** Converts a {@link PrincipalType} to the Zeebe {@link EntityType}. */
  private EntityType toEntityType(final PrincipalType principalType) {
    return switch (principalType) {
      case USER -> EntityType.USER;
      case CLIENT -> EntityType.CLIENT;
    };
  }

  private record MembershipCacheKey(
      EntityType entityType, String entityId, RelationType relationType) {}

  /** Loads memberships from {@link MembershipState}, bypassing the cache. */
  private static final class MembershipCacheLoader
      implements CacheLoader<MembershipCacheKey, List<String>> {

    private final MembershipState membershipState;

    private MembershipCacheLoader(final MembershipState membershipState) {
      this.membershipState = membershipState;
    }

    @Override
    public List<String> load(final MembershipCacheKey key) {
      LOG.trace(
          "Loading memberships for entity {}/{}, relation {}",
          key.entityType(),
          key.entityId(),
          key.relationType());
      return membershipState.getMemberships(key.entityType(), key.entityId(), key.relationType());
    }
  }
}
