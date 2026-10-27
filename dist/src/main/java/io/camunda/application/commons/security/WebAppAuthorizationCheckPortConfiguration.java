/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.security.api.context.PropertyAuthorizationEvaluator;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.authz.ScopedAuthorizationCheckPortFactory;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.impl.SearchAuthorizationScopeRepository;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the webapp-facing {@link AuthorizationCheckPort}, replacing the legacy {@code
 * ResourcePermissionPort}/{@code AuthorizationRepositoryPort} trio (issue #399).
 *
 * <p>Gated on the same condition as {@link AuthorizationScopeRepositoryConfiguration}'s root {@link
 * AuthorizationScopeRepositoryPort} bean (unconditional), rather than on secondary storage itself:
 * with secondary storage disabled there is exactly one authorization source and this bean seeds a
 * single {@code default}-keyed scope so every request resolves to it, so this bean constructs and
 * behaves correctly in every storage mode.
 *
 * <p>{@code @ConditionalOnMissingBean(AuthorizationCheckPort.class)} lets this user configuration
 * win the race against {@code camunda-security-library}'s own default {@code
 * AuthorizationConfiguration#authorizationService} bean (gated the same way): user configurations
 * discovered via {@code @ComponentScan} register before the library's
 * {@code @ImportAutoConfiguration}-imported configuration, per Spring Boot's ordering guarantee.
 * That CSL default builds a single {@code AuthorizationService} from a single {@code
 * AuthorizationChecker} bean, with no per-physical-tenant fan-out, so it cannot serve this repo's
 * requirement of one {@code AuthorizationCheckPort} per physical tenant; this bean is the required
 * override, not a redundant duplicate.
 *
 * <p>{@link LazyTokenClaimsConverter} and {@link MembershipPort} are only registered as beans under
 * the {@code consolidated-auth} profile (via {@code io.camunda.authentication}'s component scan),
 * which is only auto-activated for webapp profiles with no authentication method explicitly
 * configured. A bare Zeebe broker/gateway satisfies {@code @ConditionalOnAnyHttpGatewayEnabled}
 * without ever activating that profile, so this bean must tolerate the absence of both rather than
 * requiring OIDC/webapp wiring just to construct: it builds its own {@link
 * LazyTokenClaimsConverter} when none is available, backed by a {@link MembershipPort} that throws
 * if actually invoked. Construction never calls it, and a host with neither bean never reaches a
 * check that would (no principal claims, or authorizations disabled), so a real gap here fails
 * loudly instead of silently reporting empty memberships.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class WebAppAuthorizationCheckPortConfiguration {

  @Bean
  @ConditionalOnMissingBean(AuthorizationCheckPort.class)
  public AuthorizationCheckPort authorizationCheckPort(
      final AuthorizationScopeRepositoryPort defaultScopeRepository,
      final Optional<PhysicalTenantSearchClientReaders> physicalTenantSearchClientReaders,
      final List<PropertyAuthorizationEvaluator<?>> propertyAuthorizationEvaluators,
      final CamundaSecurityLibraryProperties securityProperties,
      final ObjectProvider<LazyTokenClaimsConverter> claimsConverter) {
    final var resolver =
        claimsConverter.getIfAvailable(() -> defaultClaimsConverter(securityProperties));
    // Equivalent to "the map would be empty" (the condition AuthorizationCheckerProvider used):
    // PhysicalTenantResolver always synthesizes a "default" entry when none is explicitly
    // configured, so whenever PhysicalTenantSearchClientReaders exists at all, its map is
    // guaranteed non-empty.
    final boolean hasPerTenantScopes = physicalTenantSearchClientReaders.isPresent();
    final Map<String, AuthorizationScopeRepositoryPort> scopeRepositoriesByScope =
        hasPerTenantScopes
            ? perTenantScopeRepositories(physicalTenantSearchClientReaders.get())
            : Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, defaultScopeRepository);
    if (hasPerTenantScopes && scopeRepositoriesByScope.isEmpty()) {
      throw new IllegalStateException(
          "PhysicalTenantSearchClientReaders is present but declares no physical tenants; "
              + "expected PhysicalTenantResolver to always synthesize a '"
              + PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID
              + "' entry. This indicates a broken invariant between PhysicalTenantResolver and "
              + "this bean -- every authorization check, including default-tenant ones, would "
              + "otherwise fail hard per request instead of at startup.");
    }
    final var checkPorts =
        ScopedAuthorizationCheckPortFactory.create(
            scopeRepositoriesByScope,
            resolver,
            propertyAuthorizationEvaluators,
            securityProperties.getAuthorizations().isEnabled(),
            securityProperties.getMultiTenancy().isChecksEnabled());
    return new TenantAwareAuthorizationCheckPort(checkPorts, hasPerTenantScopes, resolver);
  }

  private static Map<String, AuthorizationScopeRepositoryPort> perTenantScopeRepositories(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders) {
    final Map<String, AuthorizationScopeRepositoryPort> scopeRepositories = new LinkedHashMap<>();
    physicalTenantSearchClientReaders
        .readersByPhysicalTenant()
        .forEach(
            (tenantId, searchClientReaders) ->
                scopeRepositories.put(
                    tenantId,
                    new SearchAuthorizationScopeRepository(
                        searchClientReaders.authorizationReader())));
    return Map.copyOf(scopeRepositories);
  }

  /**
   * Only reached when no {@link LazyTokenClaimsConverter} bean exists at all. CSL's {@code
   * CamundaAuthenticationBeansConfiguration.lazyTokenClaimsConverter} is
   * {@code @ConditionalOnBean(MembershipPort.class)}, and every host in this repo that registers a
   * real {@link MembershipPort} also activates that CSL bean (see class javadoc), so this fallback
   * never runs with a real {@link MembershipPort} available — the hardcoded {@link
   * UnavailableMembershipPort} here is safe.
   */
  private static LazyTokenClaimsConverter defaultClaimsConverter(
      final CamundaSecurityLibraryProperties securityProperties) {
    final var oidcConfig = securityProperties.getAuthentication().getOidc();
    return new LazyTokenClaimsConverter(
        oidcConfig.getUsernameClaim(),
        oidcConfig.getClientIdClaim(),
        oidcConfig.isPreferUsernameClaim(),
        new UnavailableMembershipPort());
  }

  /**
   * Stands in for the real {@link MembershipPort} when {@code consolidated-auth} is inactive.
   * Throws instead of returning empty memberships: a host without a real {@link MembershipPort}
   * also lacks any check path that would call it (see class javadoc), so reaching one of these
   * methods indicates that assumption broke rather than a legitimate empty result.
   */
  private static final class UnavailableMembershipPort implements MembershipPort {

    @Override
    public List<String> mappingRuleIds(final MembershipQuery query) {
      throw unavailable();
    }

    @Override
    public List<String> groupIds(final MembershipQuery query) {
      throw unavailable();
    }

    @Override
    public List<String> roleIds(final MembershipQuery query) {
      throw unavailable();
    }

    @Override
    public List<String> tenantIds(final MembershipQuery query) {
      throw unavailable();
    }

    private static UnsupportedOperationException unavailable() {
      return new UnsupportedOperationException(
          "No MembershipPort bean is available (the consolidated-auth profile is not active); "
              + "membership-based authorization checks cannot be performed on this host.");
    }
  }
}
