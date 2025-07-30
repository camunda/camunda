/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.service.TenantServices.TenantDTO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public class CamundaOidcUserServiceTest {
  private static final String REGISTRATION_ID = "test";
  private static final String TOKEN_VALUE = "{}";
  private static final Instant TOKEN_ISSUED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final Instant TOKEN_EXPIRES_AT = TOKEN_ISSUED_AT.plus(1, ChronoUnit.DAYS);

  private CamundaOidcUserService camundaOidcUserService;

  @Mock private CamundaOAuthPrincipalService camundaOAuthPrincipalService;
  @Mock private JwtDecoder jwtDecoder;

  @BeforeEach
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    camundaOidcUserService = new CamundaOidcUserService(camundaOAuthPrincipalService, jwtDecoder);
  }

  @Test
  public void loadUser() {
    // given
    final Map<String, Object> claims =
        Map.of(
            "sub", "test|foo@camunda.test",
            "email", "foo@camunda.test",
            "role", "R1",
            "group", "G1");
    when(jwtDecoder.decode(TOKEN_VALUE)).thenReturn(createJwt(TOKEN_VALUE, claims));

    final var roleR1 = "roleR1";

    when(camundaOAuthPrincipalService.loadOAuthContext(claims))
        .thenReturn(
            new OAuthContext(
                Set.of("test-id", "test-id-2"),
                new AuthenticationContext.AuthenticationContextBuilder()
                    .withUsername("test")
                    .withRoles(List.of(roleR1))
                    .withGroups(List.of("G1"))
                    .withTenants(List.of(new TenantDTO(1L, "tenant-1", "Tenant One", "desc")))
                    .build()));

    // when
    final OidcUser oidcUser = camundaOidcUserService.loadUser(createOidcUserRequest(claims));

    // then
    assertThat(oidcUser).isNotNull();
    assertThat(oidcUser).isInstanceOf(CamundaOidcUser.class);
    final var camundaUser = (CamundaOidcUser) oidcUser;
    assertThat(camundaUser.getEmail()).isEqualTo("foo@camunda.test");
    assertThat(camundaUser.getMappingIds()).isEqualTo(Set.of("test-id", "test-id-2"));
    final AuthenticationContext authenticationContext = camundaUser.getAuthenticationContext();
    assertThat(authenticationContext.roles()).containsAll(Set.of(roleR1));
    assertThat(authenticationContext.tenants()).hasSize(1);
    assertThat(authenticationContext.tenants().get(0).tenantId()).isEqualTo("tenant-1");
    assertThat(authenticationContext.groups()).containsExactly("G1");
  }

  @Test
  public void clientIdIsSetInAuthContext() {
    // given
    final Map<String, Object> claims = Map.of("sub", "test|foo@camunda.test", "client_id", "blah");
    when(jwtDecoder.decode(TOKEN_VALUE)).thenReturn(createJwt(TOKEN_VALUE, claims));
    when(camundaOAuthPrincipalService.loadOAuthContext(claims))
        .thenReturn(
            new OAuthContext(
                Set.of("test-id", "test-id-2"),
                new AuthenticationContext.AuthenticationContextBuilder()
                    .withClientId("blah")
                    .build()));

    final var oidcUser = camundaOidcUserService.loadUser(createOidcUserRequest(claims));
    final var camundaUser = (CamundaOidcUser) oidcUser;
    final var authenticationContext = camundaUser.getAuthenticationContext();

    assertThat(authenticationContext.clientId()).isEqualTo("blah");
    assertThat(authenticationContext.username()).isNull();
  }

  @Test
  public void fallbackToIdTokenWhenAccessTokenDecodingFails() {
    // given
    final Map<String, Object> claims = Map.of("sub", "test|foo@camunda.test", "client_id", "blah");
    when(jwtDecoder.decode(TOKEN_VALUE)).thenThrow(new JwtException("Failed to decode"));
    when(camundaOAuthPrincipalService.loadOAuthContext(claims))
        .thenReturn(
            new OAuthContext(
                Set.of("test-id", "test-id-2"),
                new AuthenticationContext.AuthenticationContextBuilder()
                    .withClientId("blah")
                    .build()));

    final var oidcUser = camundaOidcUserService.loadUser(createOidcUserRequest(claims));
    final var camundaUser = (CamundaOidcUser) oidcUser;
    final var authenticationContext = camundaUser.getAuthenticationContext();

    assertThat(authenticationContext.clientId()).isEqualTo("blah");
    assertThat(authenticationContext.username()).isNull();
  }

  private static OidcUserRequest createOidcUserRequest(final Map<String, Object> claims) {
    return new OidcUserRequest(
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .authorizationGrantType(AuthorizationGrantType.JWT_BEARER)
            .build(),
        new OAuth2AccessToken(
            TokenType.BEARER, TOKEN_VALUE, TOKEN_ISSUED_AT, TOKEN_EXPIRES_AT, Set.of()),
        new OidcIdToken(TOKEN_VALUE, TOKEN_ISSUED_AT, TOKEN_EXPIRES_AT, claims));
  }

  private Jwt createJwt(final String tokenValue, final Map<String, Object> claims) {
    return new Jwt(tokenValue, TOKEN_ISSUED_AT, TOKEN_EXPIRES_AT, Map.of("alg", "none"), claims);
  }
}
