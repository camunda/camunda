/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.security.api.context.PropertyAuthorizationEvaluator;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.authz.PropertyAuthorizationEvaluatorRegistry;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the webapp-facing {@link AuthorizationCheckPort}, replacing the legacy {@code
 * ResourcePermissionPort}/{@code AuthorizationRepositoryPort} trio (issue #399).
 *
 * <p>Gated on the same condition as {@link AuthorizationCheckerProviderConfiguration}, the
 * collaborator whose per-tenant checkers vary with secondary storage, rather than on secondary
 * storage itself: with secondary storage disabled there is exactly one authorization source and
 * {@link AuthorizationCheckerProvider#withPhysicalTenant(String)} resolves every tenant to it, so
 * this bean constructs and behaves correctly in every storage mode.
 *
 * <p>{@code @ConditionalOnMissingBean(AuthorizationCheckPort.class)} lets this user configuration
 * win the race against {@code camunda-security-library}'s own default {@code AuthorizationService}
 * wiring (gated the same way): user configurations discovered via {@code @ComponentScan} register
 * before the library's {@code @ImportAutoConfiguration}-imported configuration, per Spring Boot's
 * ordering guarantee.
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
      final AuthorizationCheckerProvider authorizationCheckerProvider,
      final List<PropertyAuthorizationEvaluator<?>> propertyAuthorizationEvaluators,
      final CamundaSecurityLibraryProperties securityProperties,
      final ObjectProvider<LazyTokenClaimsConverter> claimsConverter) {
    return new TenantAwareAuthorizationCheckPort(
        authorizationCheckerProvider,
        new PropertyAuthorizationEvaluatorRegistry(propertyAuthorizationEvaluators),
        securityProperties.getAuthorizations().isEnabled(),
        securityProperties.getMultiTenancy().isChecksEnabled(),
        claimsConverter.getIfAvailable(() -> defaultClaimsConverter(securityProperties)));
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
