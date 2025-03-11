/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.oauth2;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.auth0.jwt.exceptions.InvalidClaimException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.impl.rest.exception.RestException;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.SpringContextHolder;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.security.tenant.OperateTenant;
import io.camunda.operate.webapp.security.tenant.TenantAwareAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      IdentityJwt2AuthenticationTokenConverter.class,
      CamundaSecurityProperties.class
    },
    properties = {OperateProperties.PREFIX + ".identity.issuerUrl = http://some.issuer.url"})
@ActiveProfiles({IDENTITY_AUTH_PROFILE, "test"})
public class IdentityJwt2AuthenticationTokenConverterIT {

  @Autowired @SpyBean private IdentityJwt2AuthenticationTokenConverter tokenConverter;

  @MockBean private Identity identity;

  @MockBean private Tenants tenants;

  @Mock private Authentication authentication;

  @SpyBean private SecurityConfiguration securityConfiguration;

  @Autowired private ApplicationContext applicationContext;

  @Before
  public void setup() {
    new SpringContextHolder().setApplicationContext(applicationContext);
  }

  @Test(expected = InsufficientAuthenticationException.class)
  public void shouldFailIfClaimIsInvalid() {
    when(identity.authentication())
        .thenThrow(
            new InvalidClaimException(
                "The Claim 'aud' value doesn't contain the required audience."));
    final Jwt token = createJwtTokenWith();
    tokenConverter.convert(token);
  }

  @Test(expected = InsufficientAuthenticationException.class)
  public void shouldFailIfTokenVerificationFails() {
    when(identity.authentication())
        .thenThrow(new RuntimeException("Any exception during token verification"));
    final Jwt token = createJwtTokenWith();
    tokenConverter.convert(token);
  }

  @Test
  public void shouldConvert() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(JwtAuthenticationToken.class);
    assertThat(authenticationToken.isAuthenticated()).isTrue();
  }

  @Test
  public void shouldReturnTenantsWhenMultiTenancyIsEnabled() throws IOException {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    doReturn(tenants).when(identity).tenants();

    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    doReturn(multiTenancyConfiguration).when(securityConfiguration).getMultiTenancy();

    final List<Tenant> tenants =
        new ObjectMapper()
            .readValue(
                getClass().getResource("/security/identity/tenants.json"),
                new TypeReference<>() {});
    doReturn(tenants).when(this.tenants).forToken(any());

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    //    //then tenants are properly converted and returned by tenant aware authentication
    final List<OperateTenant> returnedTenants = tenantAwareAuth.getTenants();

    assertThat(returnedTenants).hasSize(3);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("<default>") && t.getName().equals("Default"))
        .hasSize(1);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("tenant-a") && t.getName().equals("Tenant A"))
        .hasSize(1);

    assertThat(returnedTenants)
        .filteredOn(t -> t.getTenantId().equals("tenant-b") && t.getName().equals("Tenant B"))
        .hasSize(1);
  }

  @Test
  public void shouldReturnNullAsTenantsWhenMultiTenancyIsDisabled() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    doReturn(tenants).when(identity).tenants();
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(false);
    doReturn(multiTenancyConfiguration).when(securityConfiguration).getMultiTenancy();

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    // then no Identity is called
    assertThat(tenantAwareAuth.getTenants()).isNull();
    verifyNoInteractions(tenants);
  }

  @Test(expected = InsufficientAuthenticationException.class)
  public void shouldFailWhenGettingTenants() {
    when(identity.authentication()).thenReturn(authentication);
    when(authentication.verifyToken(any())).thenReturn(null);
    doReturn(tenants).when(identity).tenants();
    final var multiTenancyConfiguration = new MultiTenancyConfiguration();
    multiTenancyConfiguration.setEnabled(true);
    doReturn(multiTenancyConfiguration).when(securityConfiguration).getMultiTenancy();

    final Jwt token = createJwtTokenWith();
    final AbstractAuthenticationToken authenticationToken = tokenConverter.convert(token);
    assertThat(authenticationToken).isInstanceOf(TenantAwareAuthentication.class);
    final var tenantAwareAuth = (TenantAwareAuthentication) authenticationToken;

    doThrow(new RestException("smth went wrong")).when(tenants).forToken(any());
    tenantAwareAuth.getTenants();
  }

  protected Jwt createJwtTokenWith() {
    return Jwt.withTokenValue("token")
        .audience(List.of("audience"))
        .header("alg", "HS256")
        .claim("foo", "bar")
        .build();
  }
}
