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
 * <p>{@link LazyTokenClaimsConverter} is only registered as a bean under OIDC authentication
 * ({@code OidcOverrideBeansConfiguration}). {@code camunda-security-library}'s own {@code
 * AuthorizationService} -- constructed per call by {@link TenantAwareAuthorizationCheckPort} --
 * requires a non-null converter unconditionally, so BASIC/UNPROTECTED hosts fall back to building
 * one directly from the already-available {@link MembershipPort} bean (registered regardless of
 * authentication method) rather than requiring OIDC just to satisfy that constructor.
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
      final MembershipPort membershipPort,
      final ObjectProvider<LazyTokenClaimsConverter> claimsConverter) {
    return new TenantAwareAuthorizationCheckPort(
        authorizationCheckerProvider,
        new PropertyAuthorizationEvaluatorRegistry(propertyAuthorizationEvaluators),
        securityProperties.getAuthorizations().isEnabled(),
        securityProperties.getMultiTenancy().isChecksEnabled(),
        claimsConverter.getIfAvailable(
            () -> defaultClaimsConverter(securityProperties, membershipPort)));
  }

  private static LazyTokenClaimsConverter defaultClaimsConverter(
      final CamundaSecurityLibraryProperties securityProperties,
      final MembershipPort membershipPort) {
    final var oidcConfig = securityProperties.getAuthentication().getOidc();
    return new LazyTokenClaimsConverter(
        oidcConfig.getUsernameClaim(),
        oidcConfig.getClientIdClaim(),
        oidcConfig.isPreferUsernameClaim(),
        membershipPort);
  }
}
