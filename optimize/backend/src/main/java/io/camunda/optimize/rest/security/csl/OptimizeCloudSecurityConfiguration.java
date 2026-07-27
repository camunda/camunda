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
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.util.ArrayList;
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
 * <p>This deliberately aligns Optimize with OC and changes behaviour relative to Optimize 8.9 (see
 * the PR description's "Differences vs 8.9"): 8.9 denied login on a missing/malformed orgs claim,
 * required an allowed org role, and rejected bearer tokens without a cluster id. Under CSL,
 * fine-grained access is decided by the authorization policy, not by the Auth0 org role.
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
   * default to append the lenient SaaS org and cluster gates.
   */
  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final ConfigurationService configurationService) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    final List<OAuth2TokenValidator<Jwt>> extraValidators = new ArrayList<>();
    if (StringUtils.isNotBlank(cloud.getOrganizationId())) {
      extraValidators.add(new OptimizeCloudOrganizationValidator(cloud.getOrganizationId()));
    }
    if (StringUtils.isNotBlank(cloud.getClusterId())) {
      extraValidators.add(new OptimizeCloudClusterValidator(cloud.getClusterId()));
    }
    return new TokenValidatorFactory(
        oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
        OidcConfiguration.DEFAULT_CLOCK_SKEW,
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
}
