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
import io.camunda.security.spring.CamundaSecurityConfiguration;
import io.camunda.security.spring.handler.AuthFailureHandlerConfiguration;
import io.camunda.security.spring.scope.ScopedSecurityChainConfiguration;
import io.camunda.security.spring.security.BaseSecurityConfiguration;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Startup behaviour of the scoped chains on a cluster that <em>has</em> a physical tenant
 * configured, through the real {@code ScopedSecurityChainRegistrar} — which builds a webapp chain
 * as well as an API chain for every descriptor.
 *
 * <p>The configured tenant is load-bearing: with none, no scoped chain is built at all and every
 * assertion here would pass or fail for the wrong reason. That zero-tenant shape — where the
 * cluster chain's prefixed path sets serve the alias instead — is covered by {@link
 * PhysicalTenantAliasCoverageIT}.
 *
 * <p>{@link PhysicalTenantDefaultAliasChainIT} builds API chains by hand to check request outcomes,
 * so it never sees the webapp chain. This one does.
 */
class PhysicalTenantScopedChainStartupIT {

  private static final String SCOPED_LOGIN = "/physical-tenants/default/login";
  private static final String SCOPED_API = "/physical-tenants/default/v2/resource";

  // At least one key under camunda.physical-tenants.<id>.* so the tenant is discovered, which is
  // what makes the provider emit descriptors — the default alias among them.
  private static final String PHYSICAL_TENANT =
      "camunda.physical-tenants.tenanta.security.authentication.method=oidc";

  private static final String[] OIDC_PROVIDER = {
    "camunda.security.authentication.method=oidc",
    "camunda.security.authentication.oidc.client-id=example",
    "camunda.security.authentication.oidc.authorization-uri=https://authorization.example.com",
    "camunda.security.authentication.oidc.token-uri=https://token.example.com",
    "camunda.security.authentication.oidc.jwk-set-uri=https://jwks.example.com",
  };

  static Stream<Arguments> authMethods() {
    return Stream.of(
        Arguments.of("oidc", OIDC_PROVIDER),
        Arguments.of("basic", new String[] {"camunda.security.authentication.method=basic"}));
  }

  /**
   * Basic auth only. OC cannot start with {@code method=oidc} and the webapp disabled — {@code
   * OidcOverrideBeansConfiguration}'s {@code authorizedClientManager} needs a {@code
   * ClientRegistrationRepository}, which CSL publishes only while the webapp is enabled — so there
   * is no OIDC configuration for this gate to act on.
   *
   * <p>Nor is it paired with {@code oidc} and no {@code client-id}: the scoped <em>API</em> chain
   * needs a provider whatever the webapp setting is, so that cluster cannot start either way.
   */
  @Test
  void shouldNotServeScopedLoginWhenWebappDisabled() {
    runnerWith("camunda.security.authentication.method=basic")
        .withPropertyValues("camunda.security.authentication.webapp-enabled=false")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              // Without this precondition the assertion below is satisfied by two different causes:
              // the gate emptying webappPaths(), or no scoped chain having been built at all.
              assertThat(ctx.getBeanNamesForType(SecurityFilterChain.class))
                  .as("the gate is only observable if scoped chains were actually built")
                  .anyMatch(name -> name.startsWith("scoped"));
              assertThat(matchesAnyChain(ctx, SCOPED_LOGIN))
                  .as("no scoped chain may serve the scoped login path when the webapp is disabled")
                  .isFalse();
            });
  }

  /** Over-gating guard: the gate must not suppress the webapp surface when it is enabled. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("authMethods")
  void shouldServeScopedLoginWhenWebappEnabled(final String method, final String[] properties) {
    runnerWith(properties)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(matchesAnyChain(ctx, SCOPED_LOGIN))
                  .as("the scoped webapp chain must serve the login path when enabled")
                  .isTrue();
            });
  }

  @Test
  void shouldStillServeScopedApiWhenWebappDisabled() {
    // The gate must only affect the webapp chain — the API chain is the point of the fix.
    runnerWith(OIDC_PROVIDER)
        .withPropertyValues("camunda.security.authentication.webapp-enabled=false")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(matchesAnyChain(ctx, SCOPED_API))
                  .as("the scoped API chain must still serve the default alias")
                  .isTrue();
            });
  }

  private WebApplicationContextRunner runnerWith(final String... properties) {
    return new WebApplicationContextRunner()
        .withUserConfiguration(ObjectMapperConfig.class, OcPathsConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                ScopedSecurityChainConfiguration.class,
                AuthFailureHandlerConfiguration.class,
                PhysicalTenantSecurityConfiguration.class))
        // Production supplies this via BasicAuthUserDetailsPort; it exists only so the basic-auth
        // chains can be assembled, and no test authenticates as this user. Registered on the runner
        // rather than in a @Configuration, which would be component-scanned into other tests'
        // contexts and back CSL's own basic-auth beans off.
        .withBean(
            UserDetailsService.class,
            () ->
                new InMemoryUserDetailsManager(
                    User.withUsername("test").password("{noop}test").authorities("USER").build()))
        .withPropertyValues(PHYSICAL_TENANT)
        .withPropertyValues(properties);
  }

  /** Whether any registered scoped chain claims the given path. */
  private boolean matchesAnyChain(final AssertableWebApplicationContext ctx, final String path) {
    final var request = new MockHttpServletRequest("GET", path);
    return Arrays.stream(ctx.getBeanNamesForType(SecurityFilterChain.class))
        .filter(name -> name.startsWith("scoped"))
        .map(name -> ctx.getBean(name, SecurityFilterChain.class))
        .anyMatch(chain -> chain.matches(request));
  }

  @Configuration
  static class ObjectMapperConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
