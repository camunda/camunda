/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.service;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;
import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.configuration.MembershipCacheConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;

@Service
@Primary
@ConditionalOnSecondaryStorageEnabled
public class DefaultMembershipService implements MembershipService {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultMembershipService.class);

  private final MappingRuleServices mappingRuleServices;
  private final TenantServices tenantServices;
  private final RoleServices roleServices;
  private final GroupServices groupServices;
  private final OidcGroupsLoader oidcGroupsLoader;
  private final boolean isGroupsClaimConfigured;
  private final Cache<String, CamundaAuthentication> cache;
  private final MembershipCacheConfiguration cacheConfig;

  public DefaultMembershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    this.mappingRuleServices = mappingRuleServices;
    this.tenantServices = tenantServices;
    this.roleServices = roleServices;
    this.groupServices = groupServices;
    oidcGroupsLoader =
        new OidcGroupsLoader(securityConfiguration.getAuthentication().getOidc().getGroupsClaim());
    isGroupsClaimConfigured =
        securityConfiguration.getAuthentication().getOidc().isGroupsClaimConfigured();

    final var oidcConfig = securityConfiguration.getAuthentication().getOidc();
    final var rawCacheConfig = oidcConfig != null ? oidcConfig.getMembershipCache() : null;
    cacheConfig = rawCacheConfig != null ? rawCacheConfig : new MembershipCacheConfiguration();

    if (cacheConfig.isEnabled()) {
      cache =
          Caffeine.newBuilder()
              .maximumSize(cacheConfig.getMaxSize())
              .expireAfter(new MembershipEntryExpiry(cacheConfig.getTtl()))
              .build();
    } else {
      cache = null;
    }
  }

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType)
      throws OAuth2AuthenticationException {
    if (cache != null) {
      final String key = cacheKey(tokenClaims, principalId, principalType);
      if (key != null) {
        return cache.get(key, k -> doResolveMemberships(tokenClaims, principalId, principalType));
      }
    }
    return doResolveMemberships(tokenClaims, principalId, principalType);
  }

  private CamundaAuthentication doResolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final var ownerTypeToIds = new HashMap<EntityType, Set<String>>();

    ownerTypeToIds.put(
        principalType.equals(PrincipalType.USER) ? EntityType.USER : EntityType.CLIENT,
        Set.of(principalId));

    final var mappingRules =
        mappingRuleServices
            .getMatchingMappingRules(tokenClaims, CamundaAuthentication.anonymous())
            .map(MappingRuleEntity::mappingRuleId)
            .collect(Collectors.toSet());

    if (!mappingRules.isEmpty()) {
      ownerTypeToIds.put(MAPPING_RULE, mappingRules);
    } else {
      LOG.debug("No mappingRules found for these claims: {}", tokenClaims);
    }

    final Set<String> groups;
    if (isGroupsClaimConfigured) {
      groups = new HashSet<>(oidcGroupsLoader.load(tokenClaims));
    } else {
      groups =
          groupServices
              .getGroupsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
              .stream()
              .map(GroupEntity::groupId)
              .collect(Collectors.toSet());
    }

    if (!groups.isEmpty()) {
      ownerTypeToIds.put(GROUP, groups);
    }

    final var roles =
        roleServices
            .getRolesByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(RoleEntity::roleId)
            .collect(Collectors.toSet());

    if (!roles.isEmpty()) {
      ownerTypeToIds.put(EntityType.ROLE, roles);
    }

    final var tenants =
        tenantServices
            .getTenantsByMemberTypeAndMemberIds(ownerTypeToIds, CamundaAuthentication.anonymous())
            .stream()
            .map(TenantEntity::tenantId)
            .toList();

    return CamundaAuthentication.of(
        a -> {
          if (principalType.equals(PrincipalType.CLIENT)) {
            a.clientId(principalId);
          } else {
            a.user(principalId);
          }
          return a.roleIds(roles.stream().toList())
              .groupIds(groups.stream().toList())
              .mappingRule(mappingRules.stream().toList())
              .tenants(tenants)
              .claims(tokenClaims);
        });
  }

  /**
   * Derives a stable cache key from token claims + principal. Uses {@code jti} when available
   * (globally unique per token); falls back to {@code iss + sub + iat + exp} otherwise. Returns
   * {@code null} when not enough claims are present to build a safe key — callers must bypass the
   * cache in that case.
   */
  private static String cacheKey(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {
    final Object iss = tokenClaims.get("iss");
    if (!(iss instanceof final String issuer) || issuer.isBlank()) {
      return null;
    }
    final String principal = principalType.name() + ":" + principalId;
    final Object jti = tokenClaims.get("jti");
    if (jti instanceof final String s && !s.isBlank()) {
      return "jti:" + issuer + ":" + s + ":" + principal;
    }
    final Object sub = tokenClaims.get("sub");
    final Long iat = epochSecond(tokenClaims.get("iat"));
    final Long exp = epochSecond(tokenClaims.get("exp"));
    if (sub instanceof String && iat != null && exp != null) {
      return "sie:" + issuer + ":" + sub + ":" + iat + ":" + exp + ":" + principal;
    }
    return null;
  }

  private static Long epochSecond(final Object value) {
    if (value instanceof final Instant i) {
      return i.getEpochSecond();
    }
    if (value instanceof final Number n) {
      return n.longValue();
    }
    return null;
  }

  private static Instant tokenExpiry(final Map<String, Object> tokenClaims) {
    final Object exp = tokenClaims.get("exp");
    if (exp instanceof final Instant i) {
      return i;
    }
    if (exp instanceof final Number n) {
      return Instant.ofEpochSecond(n.longValue());
    }
    return null;
  }

  private static final class MembershipEntryExpiry
      implements Expiry<String, CamundaAuthentication> {
    private final Duration configuredTtl;

    MembershipEntryExpiry(final Duration configuredTtl) {
      this.configuredTtl = configuredTtl;
    }

    @Override
    public long expireAfterCreate(
        final String key, final CamundaAuthentication value, final long currentTime) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterUpdate(
        final String key,
        final CamundaAuthentication value,
        final long currentTime,
        final long currentDuration) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterRead(
        final String key,
        final CamundaAuthentication value,
        final long currentTime,
        final long currentDuration) {
      return currentDuration;
    }

    private long ttlNanos(final CamundaAuthentication value) {
      final Instant tokenExp = tokenExpiry(value.claims());
      if (tokenExp == null) {
        return configuredTtl.toNanos();
      }
      final Duration untilExp = Duration.between(Instant.now(), tokenExp);
      final Duration effective =
          untilExp.isNegative() || untilExp.isZero() || untilExp.compareTo(configuredTtl) < 0
              ? untilExp
              : configuredTtl;
      if (effective.isNegative() || effective.isZero()) {
        return 0L;
      }
      return effective.toNanos();
    }
  }
}
