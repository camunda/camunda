/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.config.controllers.DummyOidcClientRegistration;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.service.PhysicalTenantMembershipContextPropagator;
import io.camunda.security.api.context.MembershipResolutionContextPropagator;
import io.camunda.security.core.port.out.MembershipPort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Checks that a booted application actually gets the host's propagator, under either authentication
 * method.
 */
class WebSecurityConfigPropagatorImportTest {

  abstract static class BaseTest {
    @MockitoBean MembershipPort membershipPort;

    @Autowired MembershipResolutionContextPropagator propagator;

    @Test
    void shouldImportThePhysicalTenantPropagator() {
      // given a context booted the way production boots
      // when the propagator is resolved
      // then it is the host's, not the library's no-op default
      assertThat(propagator).isInstanceOf(PhysicalTenantMembershipContextPropagator.class);
    }
  }

  // Each subclass adds only what its context needs to boot and inherits BaseTest's assertion, so
  // that assertion runs once per authentication method.
  @Nested
  @SpringBootTest(
      classes = WebSecurityConfigTestContext.class,
      properties = "camunda.security.authentication.method=basic")
  @ActiveProfiles("consolidated-auth")
  class BasicAuthTest extends BaseTest {}

  @Nested
  @SpringBootTest(
      classes = WebSecurityConfigTestContext.class,
      properties = "camunda.security.authentication.method=oidc")
  @ActiveProfiles("consolidated-auth")
  class OidcAuthTest extends BaseTest {
    @TestBean private ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean private JwtDecoder jwtDecoder;

    static ClientRegistrationRepository clientRegistrationRepository() {
      return DummyOidcClientRegistration.repository();
    }
  }
}
