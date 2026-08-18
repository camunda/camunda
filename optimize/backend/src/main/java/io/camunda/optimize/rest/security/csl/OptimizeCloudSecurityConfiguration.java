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
 * CCSaaS security wiring for the CSL adoption, active under the cloud profile whenever CSL is
 * active — the default since 8.10 (camunda/camunda#58483), or opted out of with {@code
 * optimize.security.csl.enabled=false} through 8.10. Restores the SaaS org/cluster validation the
 * legacy {@code CCSaaSSecurityConfigurerAdapter} performed, using CSL's host extension points. See
 * <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>Mirrors OC's {@code OidcOverrideBeansConfiguration}: one shared {@link TokenValidatorFactory}
 * carries an {@link OptimizeCloudOrganizationValidator} and an {@link
 * OptimizeCloudClusterValidator}, and {@code idTokenDecoderFactory} reuses it. Both validators are
 * lenient on claim absence, so the single chain serves both the login id_token (carries {@code
 * orgs}, not the cluster id) and M2M bearer tokens (carry the cluster id, not {@code orgs}). Unlike
 * OC, Optimize keeps the org role gate.
 *
 * <p>The Auth0 {@code audience} authorize-request parameter and the clusterId-derived servlet
 * context path come from configuration, not code: the config compatibility bridge maps the legacy
 * Optimize keys to {@code camunda.security.*}, and CSL injects the {@code audience} parameter
 * natively.
 */
@Configuration
@Conditional(CCSaaSCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCloudSecurityConfiguration {

  /**
   * Shared token validation for the login id_token and bearer/public-API tokens. Overrides CSL's
   * {@code @ConditionalOnMissingBean} default to append the SaaS org and cluster gates. The
   * organization id and cluster id must be configured: a blank value fails startup rather than
   * silently disabling a gate.
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
   * login token runs through the same org/cluster gates as bearer tokens, overriding CSL's
   * {@code @ConditionalOnMissingBean} default.
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
