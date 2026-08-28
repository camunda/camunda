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
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.handler.AuthFailureHandlerConfiguration;
import io.camunda.security.spring.oidc.JWSKeySelectorFactory;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.ScopedClientRegistrationFactory;
import io.camunda.security.spring.oidc.ScopedJwtDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilder;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilderConfiguration;
import io.camunda.security.spring.security.BaseSecurityConfiguration;
import io.camunda.security.spring.security.DefaultWebSessionFilterConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * Integration test for the reachability of the implicit {@code default} physical-tenant alias, in
 * both cluster shapes: served by the cluster API chain's prefixed path sets when no {@code
 * camunda.physical-tenants.*} entries exist, and by its own scoped chain when they do.
 *
 * <p>Asserts a real bearer token is <em>accepted</em>, not merely that some chain claims the path —
 * {@link PhysicalTenantAliasCoverageIT} covers claiming, and a chain can claim a path and then
 * answer 401.
 *
 * <p>Distinct from {@link PhysicalTenantApiChainIsolationIT}, which covers isolation
 * <em>between</em> explicitly configured tenants and therefore runs with an empty root OIDC config.
 * Here the root config always carries the provider, and the subject under test is whether {@code
 * /physical-tenants/default} is reachable at all.
 */
class PhysicalTenantDefaultAliasChainIT {

  /** basePath + the host's apiPaths() = /physical-tenants/default/v2/** */
  private static final String DEFAULT_ALIAS_PATH = "/physical-tenants/default/v2/resource";

  // At least one key under camunda.physical-tenants.<id>.* so the tenant is discovered, which is
  // what makes the provider emit descriptors — the default alias among them.
  private static final String PHYSICAL_TENANT =
      "camunda.physical-tenants.tenanta.security.authentication.method=oidc";

  static Stream<Arguments> bothClusterShapes() {
    return Stream.of(
        Arguments.of("no physical tenants", new String[] {}),
        Arguments.of("one physical tenant", new String[] {PHYSICAL_TENANT}));
  }

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

  @ParameterizedTest(name = "{0}")
  @MethodSource("bothClusterShapes")
  void shouldAcceptRootIssuerTokenOnDefaultAlias(
      final String shape, final String[] shapeProperties) {
    // Whichever mechanism serves the alias, a root-issuer bearer token must authenticate. That is
    // the behaviour this alias exists for: the mechanism differs per shape, the outcome must not.
    final var properties = propertiesFor(shapeProperties);
    buildRunner(properties)
        .run(
            ctx -> {
              // given — a cluster with a root OIDC provider
              final var proxy = new FilterChainProxy(buildChains(ctx, envWith(properties)));
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("bothClusterShapes")
  void shouldReturn404ForUnknownPhysicalTenant(final String shape, final String[] shapeProperties) {
    // The prefixed cluster paths carry the literal "default", never a wildcard — so an unrelated
    // tenant id still falls through to the catch-all in both shapes.
    final var properties = propertiesFor(shapeProperties);
    buildRunner(properties)
        .run(
            ctx -> {
              // given
              final var proxy = new FilterChainProxy(buildChains(ctx, envWith(properties)));
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
   * The same property array feeds this context and the {@link MockEnvironment} in {@link #envWith}.
   * They are two separate consumers — CSL's properties bean and the {@link SecurityPathPort} read
   * the context, the scope provider reads the environment — and for the PT-configured shape both
   * must agree, or the port would prefix paths a scoped chain already owns.
   */
  private WebApplicationContextRunner buildRunner(final String... properties) {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ObjectMapperConfig.class, OcPathsConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                ScopedApiSecurityChainBuilderConfiguration.class,
                DefaultWebSessionFilterConfiguration.class,
                AuthFailureHandlerConfiguration.class))
        .withPropertyValues(properties);
  }

  /** Root OIDC config plus the shape's extra keys. */
  private String[] propertiesFor(final String... shapeProperties) {
    return Stream.concat(
            Stream.of(
                "camunda.security.authentication.method=oidc",
                "camunda.security.authentication.oidc.client-id=root-client",
                "camunda.security.authentication.oidc.issuer-uri=" + rootServer.issuerUri(),
                "camunda.security.authentication.oidc.jwk-set-uri="
                    + rootServer.issuerUri()
                    + "/jwks",
                "camunda.security.authentication.oidc.redirect-uri={baseUrl}/sso-callback"),
            Stream.of(shapeProperties))
        .toArray(String[]::new);
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
    // The cluster API chain, assembled exactly as OidcApiSecurityConfiguration does — its matchers
    // come from the real SecurityPathPort, so with no physical tenant configured they carry the
    // /physical-tenants/default-prefixed variants. This is what serves the alias in that shape.
    final var pathPort = ctx.getBean(SecurityPathPort.class);
    final var clusterAuth = ctx.getBean(CamundaSecurityLibraryProperties.class).getAuthentication();
    try {
      chains.add(
          chainBuilder.buildOidcApiChain(
              ctx.getBean(HttpSecurity.class),
              pathPort.apiPaths(),
              pathPort.unprotectedApiPaths(),
              scopedJwtDecoderFactory.buildIssuerAwareDecoder(clusterAuth),
              ctx.getBean(SessionRepositoryFilter.class)));
    } catch (final Exception ex) {
      throw new IllegalStateException("Failed to build the cluster API chain", ex);
    }
    // A request matching neither lands here: `/**` denyAll, answered as 404.
    chains.add(
        ctx.getBean("protectedUnhandledPathsSecurityFilterChain", SecurityFilterChain.class));
    return chains;
  }

  // =========================================================================
  // Environment builders
  // =========================================================================

  /**
   * The same {@code key=value} properties the context gets, as the scope provider's environment.
   */
  private MockEnvironment envWith(final String... properties) {
    final var env = new MockEnvironment();
    for (final String property : properties) {
      final int separator = property.indexOf('=');
      env.setProperty(property.substring(0, separator), property.substring(separator + 1));
    }
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
