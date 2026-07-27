/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.rest.security.oauth.CustomClaimValidator;
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
 * <p>Reproduces the 8.9 baseline's <em>path-specific</em> split, both checks strict on their own
 * path. It uses OC's {@code JwtDecoderFactory<ClientRegistration>} mechanism for the login gate,
 * but deliberately does not share one lenient validator factory across both paths (which would
 * weaken the bearer cluster check and the login org check):
 *
 * <ul>
 *   <li>{@code idTokenDecoderFactory} validates the interactive login id_token with a strict {@link
 *       OptimizeCloudOrganizationValidator} (organization membership + allowed role). The login
 *       id_token carries the {@code orgs} claim but not the cluster id, so only the org gate
 *       applies here.
 *   <li>{@link TokenValidatorFactory} (the bearer/public-API decoder) appends a strict cluster-id
 *       {@link CustomClaimValidator}. Machine-to-machine bearer tokens carry the cluster id but no
 *       org membership, so only the cluster gate applies here.
 * </ul>
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

  static final String CLUSTER_ID_CLAIM = "https://camunda.com/clusterId";

  /**
   * Bearer/public-API token validation: CSL ships timestamp/issuer/audience; this overrides its
   * {@code @ConditionalOnMissingBean} default to add a strict cluster-id check. No org gate here.
   */
  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final ConfigurationService configurationService) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    final List<OAuth2TokenValidator<Jwt>> extraValidators = new ArrayList<>();
    if (StringUtils.isNotBlank(cloud.getClusterId())) {
      extraValidators.add(new CustomClaimValidator(CLUSTER_ID_CLAIM, cloud.getClusterId()));
    }
    return new TokenValidatorFactory(
        oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
        OidcConfiguration.DEFAULT_CLOCK_SKEW,
        extraValidators);
  }

  /**
   * Interactive login id_token validation. Composes CSL's base validators (timestamp/issuer/
   * audience) with a strict organization + role gate, and does not carry the bearer path's
   * cluster-id check. Overrides CSL's {@code @ConditionalOnMissingBean} default so the webapp login
   * is organization-gated.
   */
  @Bean
  public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final ConfigurationService configurationService) {
    final CloudAuthConfiguration cloud = cloudConfig(configurationService);
    final List<OAuth2TokenValidator<Jwt>> extraValidators = new ArrayList<>();
    if (StringUtils.isNotBlank(cloud.getOrganizationId())) {
      extraValidators.add(
          new OptimizeCloudOrganizationValidator(
              cloud.getOrganizationId(), OptimizeCloudOrganizationValidator.ALLOWED_ORG_ROLES));
    }
    final TokenValidatorFactory idTokenValidatorFactory =
        new TokenValidatorFactory(
            oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
            OidcConfiguration.DEFAULT_CLOCK_SKEW,
            extraValidators);
    final OidcIdTokenDecoderFactory decoderFactory = new OidcIdTokenDecoderFactory();
    decoderFactory.setJwtValidatorFactory(idTokenValidatorFactory::createTokenValidator);
    return decoderFactory;
  }

  private static CloudAuthConfiguration cloudConfig(
      final ConfigurationService configurationService) {
    return configurationService.getAuthConfiguration().getCloudAuthConfiguration();
  }
}
