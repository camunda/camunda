/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.api.model.config.ScopedSecurityDescriptor;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.handler.AuthFailureHandlerConfiguration;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilder;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilderConfiguration;
import io.camunda.security.spring.security.BaseSecurityConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Integration test for the implicit {@code default} physical-tenant alias on a cluster with
 * <b>no</b> {@code camunda.physical-tenants.*} entries configured.
 *
 * <p>Distinct from {@link PhysicalTenantApiChainIsolationIT}, which covers isolation
 * <em>between</em> explicitly configured tenants and therefore runs with an empty root OIDC config.
 * Here the root config is the only config there is, and the subject under test is whether {@code
 * /physical-tenants/default} is reachable at all.
 */
class PhysicalTenantDefaultAliasChainIT {

  /** basePath + the host's apiPaths() = /physical-tenants/default/v2/** */
  private static final String DEFAULT_ALIAS_PATH = "/physical-tenants/default/v2/resource";

  private static JwksTestServer rootServer;

  @BeforeAll
  static void startServers() throws Exception {
    rootServer = JwksTestServer.start("key-root");
  }

  @AfterAll
  static void stopServers() {
    if (rootServer != null) {
      rootServer.stop();
    }
  }

  @Test
  void defaultAliasShouldAcceptRootIssuerTokenWhenNoPhysicalTenantsConfigured() {
    buildRunner()
        .run(
            ctx -> {
              // given — a cluster with a root OIDC provider and no physical tenants
              final var proxy = new FilterChainProxy(buildChains(ctx, rootOnlyEnv()));
              final var request = new MockHttpServletRequest("GET", DEFAULT_ALIAS_PATH);
              request.addHeader(
                  "Authorization",
                  "Bearer "
                      + JwksTestServer.signForIssuer(
                          rootServer, rootServer.issuerUri(), List.of()));
              final var response = new MockHttpServletResponse();
              final var downstream = new MockFilterChain();

              // when
              proxy.doFilter(request, response, downstream);

              // then
              assertThat(response.getStatus())
                  .as(
                      "a root-issuer token on the default alias must authenticate, not fall to the"
                          + " catch-all")
                  .isEqualTo(200);
              // Load-bearing, not redundant: MockHttpServletResponse starts at 200, so the status
              // assertion alone would also pass if no chain touched the request at all.
              assertThat(downstream.getRequest())
                  .as("the request must reach the downstream chain")
                  .isNotNull();
            });
  }

  @Test
  void unknownPhysicalTenantShouldReturn404WhenNoPhysicalTenantsConfigured() {
    // The default alias is the only scoped chain on this cluster; an unrelated tenant id must still
    // fall through to the catch-all rather than matching it.
    buildRunner()
        .run(
            ctx -> {
              // given
              final var proxy = new FilterChainProxy(buildChains(ctx, rootOnlyEnv()));
              final var request =
                  new MockHttpServletRequest("GET", "/physical-tenants/nonexistent/v2/resource");
              final var response = new MockHttpServletResponse();

              // when
              proxy.doFilter(request, response, new MockFilterChain());

              // then
              assertThat(response.getStatus())
                  .as("an unconfigured physical tenant must be rejected by the catch-all")
                  .isEqualTo(404);
            });
  }

  // =========================================================================
  // Chain assembly helpers
  // =========================================================================

  /**
   * The method is set here <em>and</em> on the {@link MockEnvironment} in {@link #rootOnlyEnv()} on
   * purpose: this one configures CSL's properties bean in the context, that one is what the scope
   * provider reads. They are two separate consumers, not a duplicated setting.
   */
  private WebApplicationContextRunner buildRunner() {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ObjectMapperConfig.class, OcPathsConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                ScopedApiSecurityChainBuilderConfiguration.class,
                AuthFailureHandlerConfiguration.class))
        .withPropertyValues("camunda.security.authentication.method=oidc");
  }

  /**
   * Derives descriptors from OC's {@link PhysicalTenantScopeProvider} and builds one scoped API
   * chain per descriptor, then appends CSL's catch-all last (lowest precedence).
   */
  private List<SecurityFilterChain> buildChains(
      final ApplicationContext ctx, final MockEnvironment env) {
    final var descriptors = new PhysicalTenantScopeProvider(env).get();

    final var jwsKeySelectorFactory = new JWSKeySelectorFactory();
    // Placeholder: the operative per-scope validator is built inside buildIssuerAwareDecoder from
    // each descriptor's own provider map.
    final var globalValidatorFactory =
        new TokenValidatorFactory(Map.of(), OidcConfiguration.DEFAULT_CLOCK_SKEW, List.of());
    final var scopedJwtDecoderFactory =
        new ScopedJwtDecoderFactory(
            new ScopedClientRegistrationFactory(),
            new OidcAccessTokenDecoderFactory(jwsKeySelectorFactory, globalValidatorFactory));
    final var chainBuilder = ctx.getBean(ScopedApiSecurityChainBuilder.class);

    final var chains = new ArrayList<SecurityFilterChain>();
    for (final ScopedSecurityDescriptor descriptor : descriptors) {
      try {
        chains.add(
            chainBuilder.buildScopedApiChain(
                ctx.getBean(HttpSecurity.class),
                descriptor.basePath(),
                descriptor.authentication(),
                () ->
                    scopedJwtDecoderFactory.buildIssuerAwareDecoder(descriptor.authentication())));
      } catch (final Exception ex) {
        throw new IllegalStateException("Failed to build chain for " + descriptor.basePath(), ex);
      }
    }
    // A request matching no scoped chain lands here: `/**` denyAll, answered as 404.
    chains.add(
        ctx.getBean("protectedUnhandledPathsSecurityFilterChain", SecurityFilterChain.class));
    return chains;
  }

  // =========================================================================
  // Environment builders
  // =========================================================================

  /** Root/cluster OIDC provider only — deliberately no {@code camunda.physical-tenants.*} keys. */
  private MockEnvironment rootOnlyEnv() {
    final var env = new MockEnvironment();
    env.setProperty("camunda.security.authentication.method", "oidc");
    env.setProperty("camunda.security.authentication.oidc.client-id", "root-client");
    env.setProperty("camunda.security.authentication.oidc.issuer-uri", rootServer.issuerUri());
    env.setProperty(
        "camunda.security.authentication.oidc.jwk-set-uri", rootServer.issuerUri() + "/jwks");
    env.setProperty("camunda.security.authentication.oidc.redirect-uri", "{baseUrl}/sso-callback");
    return env;
  }

  // =========================================================================
  // Minimal host beans CSL's chain builder needs
  // =========================================================================

  @Configuration
  static class ObjectMapperConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
