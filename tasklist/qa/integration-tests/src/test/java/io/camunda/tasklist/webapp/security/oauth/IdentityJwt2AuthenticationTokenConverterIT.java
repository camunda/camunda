/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.oauth;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.InvalidClaimException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.SpringContextHolder;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.webapp.tenant.TenantAwareAuthentication;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityJwt2AuthenticationTokenConverter.class,
      TasklistProperties.class
    },
    properties = {
      TasklistProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url",
      "management.endpoint.health.group.readiness.include=readinessState"
    })
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class IdentityJwt2AuthenticationTokenConverterIT {

  @Autowired @SpyBean private IdentityJwt2AuthenticationTokenConverter tokenConverter;

  @MockBean private Identity identity;
  @Mock private Authentication authentication;

  @MockBean private Tenants tenants;

  @SpyBean private SecurityConfiguration securityConfiguration;

  @SpyBean private TasklistProperties tasklistProperties;

  @Autowired private ApplicationContext applicationContext;

  @BeforeEach
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
  }

  @Test
  public void shouldFailIfClaimIsInvalid() {
    when(identity.authentication())
        .thenThrow(
            new InvalidClaimException(
                "The Claim 'aud' value doesn't contain the required audience."));
    final Jwt token = createJwtTokenWith();
    assertThrows(InsufficientAuthenticationException.class, () -> tokenConverter.convert(token));
  }

  @Test
  public void shouldFailIfTokenVerificationFails() {
    when(identity.authentication())
        .thenThrow(new RuntimeException("Any exception during token verification"));
    final Jwt token = createJwtTokenWith();
    assertThrows(InsufficientAuthenticationException.class, () -> tokenConverter.convert(token));
  }

  @Test
  public void shouldConvert() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isNotNull();
    assertThat(authenticationToken).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(authenticationToken.isAuthenticated()).isTrue();
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    // given
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    when(identity.tenants()).thenReturn(tenants);

    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);

    final List<Tenant> tenants =
        CommonUtils.OBJECT_MAPPER.readValue(
            getClass().getResource("/identity/tenants.json"), new TypeReference<>() {});
    when(this.tenants.forToken(any())).thenReturn(tenants);

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    // when
    final var result = tenantAwareAuth.getTenants();

    // then
    assertThat(result)
        .extracting("id", "name")
        .containsExactly(
            tuple("<default>", "Default"),
            tuple("tenant-a", "Tenant A"),
            tuple("tenant-b", "Tenant B"),
            tuple("tenant-c", "Tenant C"));
  }

  @Test
  public void shouldReturnEmptyTenantsListWhenMultiTenancyIsDisabled() {
    // given
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    when(identity.tenants()).thenReturn(tenants);

    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(false);
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);

    // when
    final var result = ((TenantAwareAuthentication) authenticationToken).getTenants();

    // then
    assertThat(result).isEmpty();
    verifyNoInteractions(tenants);
  }

  @Test
  public void shouldThrowExceptionWhen() {
    // given
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    when(identity.tenants()).thenReturn(tenants);

    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    when(securityConfiguration.getMultiTenancy()).thenReturn(multiTenancyConfiguration);

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;
    when(tenants.forToken(any())).thenThrow(new RestException("smth went wrong"));

    // when - then
    assertThatThrownBy(() -> tenantAwareAuth.getTenants())
        .isInstanceOf(InsufficientAuthenticationException.class)
        .hasMessage("smth went wrong");
  }

  protected Jwt createJwtTokenWith() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("foo", "bar")
        .build();
  }
}
