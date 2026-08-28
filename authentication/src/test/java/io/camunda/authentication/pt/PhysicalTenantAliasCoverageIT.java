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
import io.camunda.security.spring.security.BasicAuthApiSecurityConfiguration;
import io.camunda.security.spring.security.BasicAuthWebappSecurityConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Checks that the {@code default} physical-tenant alias is served in both cluster shapes — by a
 * scoped chain when tenants are configured, by the cluster chain's prefixed paths when none are —
 * and that neither mechanism reaches further than it should.
 *
 * <p>BASIC mode throughout: these tests only ask which chain matches a path, never whether a token
 * is valid, so no IdP is needed and no OIDC discovery happens.
 */
class PhysicalTenantAliasCoverageIT {

  // Matches "/**" by design, so it would claim every path asked about here. Without excluding it
  // from chainsClaiming(), every assertion below would pass vacuously.
  private static final String CATCH_ALL_CHAIN = "protectedUnhandledPathsSecurityFilterChain";

  private static final String TENANT_A =
      "camunda.physical-tenants.tenanta.security.authentication.method=basic";

  static Stream<Arguments> bothClusterShapes() {
    return Stream.of(
        Arguments.of("no physical tenants", new String[] {}),
        Arguments.of("one physical tenant", new String[] {TENANT_A}));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bothClusterShapes")
  void shouldClaimDefaultAliasInBothClusterShapes(final String shape, final String[] properties) {
    runnerWith(properties)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(chainsClaiming(ctx, "/physical-tenants/default/v2/topology"))
                  .as("some chain must claim the default alias, whichever mechanism serves it")
                  .isNotEmpty();
            });
  }

  @Test
  void shouldBuildNoScopedChainsWhenNoPhysicalTenantsConfigured() {
    // The observable effect of the descriptor gate: with nothing emitted, CSL registers no scoped
    // chains at all. That is what avoids building a second pair of chains per provider on an OIDC
    // cluster, though this BASIC-mode context cannot see the discovery calls themselves.
    runnerWith()
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBeanNamesForType(SecurityFilterChain.class))
                  .noneMatch(name -> name.startsWith("scoped"));
            });
  }

  @Test
  void shouldClaimNoPathCarryingTheDefaultTenantPrefixTwice() {
    // A scoped chain prefixes the host's paths with its own base path, so exposing the prefixed
    // variants while descriptors exist would prefix them twice.
    runnerWith(TENANT_A)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(
                      chainsClaiming(
                          ctx, "/physical-tenants/tenanta/physical-tenants/default/v2/topology"))
                  .isEmpty();
            });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("bothClusterShapes")
  void shouldClaimNoPathForAnUnconfiguredTenant(final String shape, final String[] properties) {
    // An unknown tenant must fall through to the catch-all in both shapes. This is what stops the
    // prefixed cluster paths from answering for every tenant id — they carry the literal "default",
    // never a wildcard.
    runnerWith(properties)
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(chainsClaiming(ctx, "/physical-tenants/unconfigured/v2/topology"))
                  .isEmpty();
            });
  }

  @Test
  void shouldClaimAliasRootOnlyWithTrailingSlash() {
    // Pre-existing: CSL's scoped webapp chain already derives "/" as basePath + "/", so the
    // prefixed cluster sets inherit it.
    runnerWith()
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(chainsClaiming(ctx, "/physical-tenants/default/")).isNotEmpty();
              assertThat(chainsClaiming(ctx, "/physical-tenants/default")).isEmpty();
            });
  }

  /**
   * Names of the chains claiming {@code path}, excluding CSL's match-everything catch-all. Returned
   * as names rather than a boolean so a failure reports which chains matched.
   */
  private static List<String> chainsClaiming(
      final AssertableWebApplicationContext ctx, final String path) {
    final var request = new MockHttpServletRequest("GET", path);
    return Arrays.stream(ctx.getBeanNamesForType(SecurityFilterChain.class))
        .filter(name -> !CATCH_ALL_CHAIN.equals(name))
        .filter(name -> ctx.getBean(name, SecurityFilterChain.class).matches(request))
        .toList();
  }

  private WebApplicationContextRunner runnerWith(final String... properties) {
    return new WebApplicationContextRunner()
        .withUserConfiguration(TestBeansConfig.class, OcPathsConfig.class)
        .withConfiguration(
            AutoConfigurations.of(
                CamundaSecurityConfiguration.class,
                BaseSecurityConfiguration.class,
                BasicAuthApiSecurityConfiguration.class,
                BasicAuthWebappSecurityConfiguration.class,
                ScopedSecurityChainConfiguration.class,
                AuthFailureHandlerConfiguration.class,
                PhysicalTenantSecurityConfiguration.class))
        .withPropertyValues("camunda.security.authentication.method=basic")
        .withPropertyValues(properties);
  }

  // Deliberately not @Configuration. Spring component-scans an annotated inner class in this
  // package into other tests' contexts, where a stray UserDetailsService backs CSL's own
  // basic-auth beans off.
  static class TestBeansConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    UserDetailsService userDetailsService() {
      return new InMemoryUserDetailsManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
      return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
  }
}
