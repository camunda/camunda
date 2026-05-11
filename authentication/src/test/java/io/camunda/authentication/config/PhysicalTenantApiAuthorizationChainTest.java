/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import io.camunda.authentication.config.controllers.TestApiController;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-end chain test for the physical-tenant URL-shape authorization rule.
 *
 * <p>Demonstrates the gate behavior on {@code /v2/physical-tenants/{ptId}/**}: requires
 * authentication, requires the {@code PT_<ptId>} authority matching the URL, fails closed
 * otherwise. {@code PT_<id>} authorities are injected per-request via {@link
 * org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#user}
 * so the test exercises the {@code AuthorizationFilter} + {@code PhysicalTenantAuthorizationManager}
 * pair without requiring an OIDC scaffold or a custom {@code UserDetailsService}.
 *
 * <p>The JWT → {@code PT_<id>} mapping (Phase 1) is covered separately in {@code
 * PhysicalTenantJwtAuthenticationConverterTest}; the URL rule applies identically on the OIDC and
 * HTTP-basic API chains because the same {@code PhysicalTenantAuthorizationManager} is injected
 * into both.
 *
 * <p>Also pins backward-compatibility commitments on the API chain: existing {@code /v2/**}
 * endpoints unchanged, {@code UNPROTECTED_API_PATHS} still bypass auth, principals without
 * {@code PT_*} are denied on tenant URLs.
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityConfig.class,
      PhysicalTenantApiAuthorizationChainTest.LocalConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=basic",
      "camunda.physical-tenants[0].id=risk-production",
      "camunda.physical-tenants[0].idps[0]=default",
      "camunda.physical-tenants[1].id=audit",
      "camunda.physical-tenants[1].idps[0]=default",
    })
public class PhysicalTenantApiAuthorizationChainTest extends AbstractWebSecurityConfigTest {

  private static final String PT_RISK_URL = "/v2/physical-tenants/risk-production/foo";
  private static final String PT_AUDIT_URL = "/v2/physical-tenants/audit/foo";

  @Test
  void shouldReturn401OnPhysicalTenantUrlWithoutAuthentication() {
    // when
    final MvcTestResult result =
        mockMvcTester.get().uri("https://localhost" + PT_RISK_URL).exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void shouldReturn200WhenPtAuthorityMatchesPathTenant() {
    // when — user has PT_risk-production, requests risk-production endpoint
    final MvcTestResult result =
        mockMvcTester
            .get()
            .with(user("alice").authorities(authorities("PT_risk-production")))
            .uri("https://localhost" + PT_RISK_URL)
            .exchange();

    // then — gate granted, dummy controller responds
    assertThat(result).hasStatusOk();
  }

  @Test
  void shouldReturn403WhenPtAuthorityIsForDifferentTenant() {
    // when — user has PT_audit, requests risk-production endpoint
    final MvcTestResult result =
        mockMvcTester
            .get()
            .with(user("alice").authorities(authorities("PT_audit")))
            .uri("https://localhost" + PT_RISK_URL)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldReturn403ForAuthenticatedPrincipalWithoutPtAuthority() {
    // given — authenticated user with only ROLE_USER (no PT_*). Pins backward-compat commitment:
    // principals lacking PT_* never reach PT URLs.

    // when
    final MvcTestResult result =
        mockMvcTester
            .get()
            .with(user("alice").authorities(authorities("ROLE_USER")))
            .uri("https://localhost" + PT_RISK_URL)
            .exchange();

    // then
    assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldGrantMultiPtUserAccessToEitherTenantUrl() {
    // when / then — multi-PT user is granted on both tenant URLs
    assertThat(
            mockMvcTester
                .get()
                .with(user("alice").authorities(authorities("PT_risk-production", "PT_audit")))
                .uri("https://localhost" + PT_RISK_URL)
                .exchange())
        .hasStatusOk();
    assertThat(
            mockMvcTester
                .get()
                .with(user("alice").authorities(authorities("PT_risk-production", "PT_audit")))
                .uri("https://localhost" + PT_AUDIT_URL)
                .exchange())
        .hasStatusOk();
  }

  @Test
  void shouldNotRegressExistingV2EndpointForUsersWithoutPtAuthority() {
    // when — /v2/foo is NOT under physical-tenants/{ptId}/, so the new rule should not apply
    final MvcTestResult result =
        mockMvcTester
            .get()
            .with(user("alice").authorities(authorities("ROLE_USER")))
            .uri("https://localhost" + TestApiController.DUMMY_V2_API_ENDPOINT)
            .exchange();

    // then — existing endpoint still authenticates normally
    assertThat(result).hasStatusOk();
  }

  @Test
  void shouldNotRegressUnprotectedV2EndpointEvenWithPtRuleActive() {
    // when — /v2/license is in UNPROTECTED_API_PATHS; rule is permitAll regardless of auth
    final MvcTestResult result =
        mockMvcTester.get().uri("https://localhost/v2/license").exchange();

    // then — no auth required (404 only because no controller is registered in the test context;
    // what matters is that the auth gate did NOT fire)
    assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
  }

  private static org.springframework.security.core.GrantedAuthority[] authorities(
      final String... names) {
    final var list =
        org.springframework.security.core.authority.AuthorityUtils.createAuthorityList(names);
    return list.toArray(new org.springframework.security.core.GrantedAuthority[0]);
  }

  @Configuration
  static class LocalConfig {
    @Bean
    PhysicalTenantTestController physicalTenantTestController() {
      return new PhysicalTenantTestController();
    }
  }

  @RestController
  static class PhysicalTenantTestController {
    @GetMapping("/v2/physical-tenants/{ptId}/foo")
    String dummyPhysicalTenantEndpoint(@PathVariable("ptId") final String ptId) {
      return "PT " + ptId;
    }
  }
}
