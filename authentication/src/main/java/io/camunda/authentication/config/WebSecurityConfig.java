/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.authentication.clusteradmin.ClusterAdminBasicSecurityConfiguration;
import io.camunda.authentication.clusteradmin.ClusterAdminConverterConfiguration;
import io.camunda.authentication.clusteradmin.ClusterAdminOidcSecurityConfiguration;
import io.camunda.authentication.config.spi.AdminUserPresenceAdapter;
import io.camunda.authentication.config.spi.BasicAuthUserDetailsAdapter;
import io.camunda.authentication.config.spi.SecurityPathAdapter;
import io.camunda.authentication.config.spi.WebAppProviderAdapter;
import io.camunda.authentication.pt.PhysicalTenantSecurityConfiguration;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.core.port.out.AdminUserPresencePort;
import io.camunda.security.core.port.out.BasicAuthUserDetailsPort;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityAutoConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.security.OidcResourceServerCustomizer;
import io.camunda.security.spring.spi.WebAppProviderPort;
import io.camunda.service.RoleServices;
import io.camunda.service.registry.ServiceRegistry;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.observation.SecurityObservationSettings;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * Host security configuration. Opts into the full camunda-security-library filter chain stack
 * through {@link CamundaSecurityAutoConfiguration} and wires the host SPI beans the library
 * requires.
 *
 * <p>The CSL umbrella is loaded via {@code @ImportAutoConfiguration} rather than plain
 * {@code @Import}: under {@code @Import} loading, Spring's {@code @ConditionalOnBean} /
 * {@code @ConditionalOnMissingBean} evaluate against a partial bean graph and CSL configurations
 * sporadically drop beans or fail to back off when the host supplies overrides
 * (camunda/camunda-security-library#173). The {@code @AutoConfiguration} umbrella shifts CSL
 * processing into the deferred phase so its conditions evaluate against the full bean graph.
 *
 * <p>OC-specific OIDC and basic-auth bean overrides live in {@link OidcOverrideBeansConfiguration}
 * and {@link BasicAuthBeansConfiguration} respectively; CSL defaults back off via
 * {@code @ConditionalOnMissingBean}.
 */
@Configuration
@Profile("consolidated-auth")
@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)
@Import({
  OidcOverrideBeansConfiguration.class,
  BasicAuthBeansConfiguration.class,
  MembershipResolutionContextPropagatorConfiguration.class,
  SaasCspModeCompatibility.class,
  PhysicalTenantSecurityConfiguration.class,
  ClusterAdminBasicSecurityConfiguration.class,
  ClusterAdminOidcSecurityConfiguration.class,
  ClusterAdminConverterConfiguration.class,
})
public class WebSecurityConfig {

  /** The host's path declarations. */
  @Bean
  public SecurityPathPort securityPathPort(final Environment environment) {
    return SecurityPathAdapter.fromEnvironment(environment);
  }

  @Bean
  public WebAppProviderPort webAppProvider() {
    return new WebAppProviderAdapter();
  }

  /**
   * Host {@link AdminUserPresencePort} backed by OC's {@link RoleServices} and the initialization
   * sub-config from {@link CamundaSecurityLibraryProperties}. CSL's {@code AdminUserCheckFilter} is
   * wired only into the basic-auth webapp chain (camunda/camunda-security-library#190); gating
   * registration on the same property keeps the port off OIDC deployments.
   */
  @Bean
  @ConditionalOnProperty(
      name = "camunda.security.authentication.method",
      havingValue = "basic",
      matchIfMissing = true)
  public AdminUserPresencePort adminUserPresencePort(
      final ServiceRegistry serviceRegistry, final CamundaSecurityLibraryProperties properties) {
    return new AdminUserPresenceAdapter(serviceRegistry, properties.getInitialization());
  }

  /**
   * Host {@link BasicAuthUserDetailsPort} resolving basic-auth users from OC's user services. Gated
   * on the basic-auth path the removed {@code CamundaUserDetailsService} ran under.
   */
  @Bean
  @ConditionalOnProperty(
      name = "camunda.security.authentication.method",
      havingValue = "basic",
      matchIfMissing = true)
  @ConditionalOnMissingBean(BasicAuthUserDetailsPort.class)
  public BasicAuthUserDetailsPort basicAuthUserDetailsPort(final ServiceRegistry serviceRegistry) {
    return new BasicAuthUserDetailsAdapter(serviceRegistry);
  }

  /** Wires OC's RFC 9728 protected-resource-metadata customiser onto the OIDC chains. */
  @Bean
  @ConditionalOnProperty(name = "camunda.security.authentication.method", havingValue = "oidc")
  public OidcResourceServerCustomizer ocOidcResourceServerCustomizer(
      final ClientRegistrationRepository clientRegistrationRepository) {
    return new ProtectedResourceMetadataCustomizer(clientRegistrationRepository);
  }

  /**
   * Allows encoded slashes ({@code %2F}) in request URIs. Required for entity IDs containing
   * forward slashes (e.g., OIDC group IDs like {@code /myGroup} from Keycloak). Without this, the
   * default {@link StrictHttpFirewall} rejects any request whose URI contains {@code %2F} with a
   * 400 error before it reaches any controller.
   *
   * @see <a href="https://github.com/camunda/camunda/issues/45215">Issue #45215</a>
   */
  @Bean
  public WebSecurityCustomizer encodedSlashFirewallCustomizer() {
    final var firewall = new StrictHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);
    return web -> web.httpFirewall(firewall);
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityObservationSettings defaultSecurityObservations() {
    return SecurityObservationSettings.withDefaults().build();
  }

  /**
   * Anonymous passthrough for unauthenticated webapp requests. CSL's {@code
   * DefaultCamundaAuthenticationProvider} unconditionally invokes the delegating converter even
   * when the Spring {@code SecurityContext} is empty; without a converter that {@code
   * supports(null) }, the delegating converter throws {@code CamundaAuthenticationException}.
   * Returning {@code null} tells CSL to clear the holder and return null to the caller — the
   * canonical anonymous-passthrough pattern (see {@code DefaultCamundaAuthenticationProvider}'s
   * contract: "If the converter returns null, the holder is cleared and null is returned to the
   * caller.").
   *
   * <p>Only registered when the API is protected. When {@code
   * camunda.security.authentication.unprotected-api=true}, CSL's {@code
   * UnprotectedCamundaAuthenticationConverter} is already active and also supports {@code null},
   * but returns an anonymous {@link CamundaAuthentication} (the correct semantics for unprotected
   * mode). Keeping these two converters mutually exclusive avoids ordering ambiguity in the
   * delegating converter's first-match resolution.
   */
  @Bean
  @ConditionalOnProperty(
      name = "camunda.security.authentication.unprotected-api",
      havingValue = "false",
      matchIfMissing = true)
  public CamundaAuthenticationConverter<Authentication>
      anonymousNullSpringAuthenticationConverter() {
    return new CamundaAuthenticationConverter<>() {
      @Override
      public boolean supports(final Authentication authentication) {
        return authentication == null;
      }

      @Override
      public CamundaAuthentication convert(final Authentication authentication) {
        return null;
      }
    };
  }
}
