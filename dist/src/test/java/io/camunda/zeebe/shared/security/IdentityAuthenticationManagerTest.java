/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.authentication.Authentication;
import io.camunda.identity.sdk.tenants.Tenants;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
final class IdentityAuthenticationManagerTest {

  private static final String AUDIENCE = "zeebe-api";

  @Mock private Identity identity;
  @Mock private Authentication identityAuthentication;
  @Mock private Tenants identityTenants;
  @Mock private IdentityConfiguration identityConfiguration;

  private final MultiTenancyConfiguration multiTenancy = new MultiTenancyConfiguration();
  private IdentityAuthenticationManager manager;

  @BeforeEach
  void setUp() {
    when(identityConfiguration.getAudience()).thenReturn(AUDIENCE);
    manager = new IdentityAuthenticationManager(identity, identityConfiguration, multiTenancy);
  }

  /**
   * verifyToken fetches the token's permissions from Identity over REST for the GENERIC and
   * MICROSOFT types, on every request.
   */
  @Test
  void shouldVerifyTheTokenLocallyWithoutFetchingPermissions() {
    // given
    final var token = token("user-1", "Jane");
    givenVerified(token);

    // when
    final var result = manager.authenticate(new PreAuthToken(token.getToken()));

    // then
    verify(identityAuthentication).verifyAndDecode(token.getToken(), AUDIENCE);
    verify(identityAuthentication, never()).verifyToken(anyString());
    verify(identityAuthentication, never()).verifyToken(anyString(), any());
    assertThat(result)
        .isInstanceOfSatisfying(
            IdentityAuthentication.class,
            authentication -> {
              assertThat(authentication.token()).isSameAs(token);
              assertThat(authentication.getCredentials()).isEqualTo(token.getToken());
              assertThat(authentication.isAuthenticated()).isTrue();
              assertThat(authentication.tenantIds())
                  .containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
            });
  }

  @Test
  void shouldRejectATokenThatFailsVerification() {
    // given
    when(identity.authentication()).thenReturn(identityAuthentication);
    when(identityAuthentication.verifyAndDecode("expired", AUDIENCE))
        .thenThrow(new TokenExpiredException("The Token has expired", Instant.now()));

    // when / then
    assertThatThrownBy(() -> manager.authenticate(new PreAuthToken("expired")))
        .isInstanceOf(BadCredentialsException.class)
        .hasCauseInstanceOf(TokenExpiredException.class);
  }

  @Test
  void shouldResolveTenantsFromIdentityWhenChecksAreEnabled() {
    // given
    multiTenancy.setChecksEnabled(true);
    final var token = token("user-1", "Jane");
    givenVerified(token);
    when(identity.tenants()).thenReturn(identityTenants);
    when(identityTenants.forToken(token.getToken()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when
    final var result =
        (IdentityAuthentication) manager.authenticate(new PreAuthToken(token.getToken()));

    // then
    assertThat(result.tenantIds()).containsExactly("tenant-a", "tenant-b");
  }

  @Test
  void shouldPassThroughAnAuthenticationThatIsNotAPreAuthToken() {
    // given
    final var other = new UsernamePasswordAuthenticationToken("user", "password");

    // when / then
    assertThat(manager.authenticate(other)).isSameAs(other);
  }

  @Test
  void shouldNameTheAuthenticationByTheNameClaim() {
    assertThat(new IdentityAuthentication(token("user-1", "Jane"), List.of()).getName())
        .isEqualTo("Jane");
  }

  @Test
  void shouldFallBackToTheSubjectWithoutANameClaim() {
    assertThat(new IdentityAuthentication(token("user-1", null), List.of()).getName())
        .isEqualTo("user-1");
  }

  private void givenVerified(final DecodedJWT token) {
    when(identity.authentication()).thenReturn(identityAuthentication);
    when(identityAuthentication.verifyAndDecode(token.getToken(), AUDIENCE)).thenReturn(token);
  }

  private static DecodedJWT token(final String subject, final String name) {
    final var builder = JWT.create().withSubject(subject).withAudience(AUDIENCE);
    if (name != null) {
      builder.withClaim("name", name);
    }
    return JWT.decode(builder.sign(Algorithm.none()));
  }
}
