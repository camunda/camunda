/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.spring.utils.PhysicalTenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class PhysicalTenantAwareOidcTokenAuthenticationConverterTest {

  private static final JwtAuthenticationToken JWT_TOKEN =
      new JwtAuthenticationToken(
          Jwt.withTokenValue("t").header("alg", "none").claim("sub", "s").build());

  private final CamundaAuthenticationConverter<Authentication> tenantAConverter = mock();
  private final CamundaAuthenticationConverter<Authentication> defaultConverter = mock();

  private final CamundaAuthentication tenantAResult =
      CamundaAuthentication.of(b -> b.user("a-user"));
  private final CamundaAuthentication defaultResult = CamundaAuthentication.of(b -> b.user("root"));

  private final PhysicalTenantAwareOidcTokenAuthenticationConverter converter =
      new PhysicalTenantAwareOidcTokenAuthenticationConverter(
          Map.of("tenanta", tenantAConverter), defaultConverter);

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void supportsOnlyJwtAuthenticationToken() {
    assertThat(converter.supports(JWT_TOKEN)).isTrue();
    assertThat(converter.supports(mock(JwtAuthenticationToken.class))).isTrue();
    assertThat(converter.supports(new UsernamePasswordAuthenticationToken("u", "p"))).isFalse();
    assertThat(converter.supports(null)).isFalse();
  }

  @Test
  void usesThePerTenantConverterWhenTheRequestTargetsThatTenant() {
    bindRequestForTenant("tenanta");
    when(tenantAConverter.convert(JWT_TOKEN)).thenReturn(tenantAResult);

    assertThat(converter.convert(JWT_TOKEN)).isSameAs(tenantAResult);
    verifyNoInteractions(defaultConverter);
  }

  @Test
  void fallsBackToTheDefaultConverterForATenantWithoutADedicatedConverter() {
    bindRequestForTenant("tenantz");
    when(defaultConverter.convert(JWT_TOKEN)).thenReturn(defaultResult);

    assertThat(converter.convert(JWT_TOKEN)).isSameAs(defaultResult);
    verifyNoInteractions(tenantAConverter);
  }

  @Test
  void fallsBackToTheDefaultConverterWhenNoTenantIsBoundToTheRequest() {
    // No request bound → PhysicalTenantContext.currentOrNull() is null (off-request).
    when(defaultConverter.convert(JWT_TOKEN)).thenReturn(defaultResult);

    assertThat(converter.convert(JWT_TOKEN)).isSameAs(defaultResult);
    verifyNoInteractions(tenantAConverter);
  }

  @Test
  void anUnstampedRequestResolvesToTheDefaultTenant() {
    // A request with no physical-tenant stamp resolves to DEFAULT_PHYSICAL_TENANT_ID; with no
    // dedicated converter for it, the default converter is used.
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    when(defaultConverter.convert(any())).thenReturn(defaultResult);

    assertThat(converter.convert(JWT_TOKEN)).isSameAs(defaultResult);
    verifyNoInteractions(tenantAConverter);
  }

  private static void bindRequestForTenant(final String tenantId) {
    final var request = new MockHttpServletRequest();
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }
}
