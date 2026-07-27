/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import io.camunda.optimize.service.util.configuration.security.CloudAuthConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * CCSaaS-specific security wiring for the CSL adoption. Active only under the cloud profile with
 * CSL enabled. Brings back the SaaS org/cluster validation the legacy {@code
 * CCSaaSSecurityConfigurerAdapter} performed, using CSL's documented host extension points. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>Mirrors OC's {@code OidcOverrideBeansConfiguration}: a single shared {@link
 * TokenValidatorFactory} carries both a lenient {@link OptimizeCloudOrganizationValidator} and a
 * lenient {@link OptimizeCloudClusterValidator}, and the {@code idTokenDecoderFactory} reuses that
 * same factory. Because both validators are lenient on claim absence, the one chain serves both the
 * interactive login id_token (carries {@code orgs}, not the cluster id) and machine-to-machine
 * bearer tokens (carry the cluster id, not {@code orgs}).
 *
 * <p>This aligns Optimize with OC's shared-factory model but keeps Optimize's org role gate. It
 * still changes behaviour relative to Optimize 8.9 (see the PR description's "Differences vs 8.9"):
 * on claim absence the checks are now lenient (a login token without the orgs claim, or a bearer
 * token without the cluster id, is no longer rejected on that ground), and the id_token now runs
 * through CSL's issuer/audience validation. The org membership + allowed-role requirement is
 * retained.
 *
 * <p>The Auth0 {@code audience} authorize-request parameter (so the login token is accepted by the
 * Accounts API) and the clusterId-derived servlet context path are supplied by configuration, not
 * code: the config compatibility bridge maps the legacy Optimize keys to {@code
 * camunda.security.*}, and CSL's authorization-request resolver injects the {@code audience}
 * parameter natively.
 */
@Configuration
@Conditional(CCSaaSCondition.class)
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
public class OptimizeCloudSecurityConfiguration {

  /**
   * Shared token validation for both the login id_token and bearer/public-API tokens. CSL ships the
   * base chain (timestamp/issuer/audience); this overrides its {@code @ConditionalOnMissingBean}
   * default to append the SaaS org and cluster gates. Both gates are lenient on claim absence, but
   * the organization id and cluster id must be configured: a blank value fails startup rather than
   * silently disabling the gate.
   */
  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final ConfigurationService configurationService,
      final CamundaSecurityLibraryProperties cslProperties) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    final String organizationId = requireConfigured(cloud.getOrganizationId(), "organizationId");
    final String clusterId = requireConfigured(cloud.getClusterId(), "clusterId");
    // Both gates are always added. CCSaaS access control must be enforced, so a blank org or
    // cluster id fails startup above rather than silently dropping a gate and failing open.
    final List<OAuth2TokenValidator<Jwt>> extraValidators =
        List.of(
            new OptimizeCloudOrganizationValidator(
                organizationId, OptimizeCloudOrganizationValidator.ALLOWED_ORG_ROLES),
            new OptimizeCloudClusterValidator(clusterId));
    return new TokenValidatorFactory(
        oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
        cslProperties.getAuthentication().getOidc().getClockSkew(),
        extraValidators);
  }

  /**
   * Interactive login id_token validation. Reuses the shared {@link #tokenValidatorFactory} so the
   * login token runs through the same org/cluster gates as bearer tokens. Overrides CSL's
   * {@code @ConditionalOnMissingBean} default so the webapp login is organization-gated.
   */
  @Bean
  public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
      final TokenValidatorFactory tokenValidatorFactory) {
    final OidcIdTokenDecoderFactory decoderFactory = new OidcIdTokenDecoderFactory();
    decoderFactory.setJwtValidatorFactory(tokenValidatorFactory::createTokenValidator);
    return decoderFactory;
  }

  private static CloudAuthConfiguration cloudConfig(
      final ConfigurationService configurationService) {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }

  private static String requireConfigured(final String value, final String name) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalStateException(
          ("CCSaaS CSL mode requires a non-blank %s: SaaS organization and cluster access control"
                  + " cannot be enforced without it. Check the cloud auth configuration.")
              .formatted(name));
    }
    return value;
  }
}
