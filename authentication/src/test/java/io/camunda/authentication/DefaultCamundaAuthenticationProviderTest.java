/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DefaultCamundaAuthenticationProviderTest {

  @Mock CamundaAuthenticationHolder holder;
  @Mock private CamundaAuthenticationConverter<Authentication> authenticationConverter;
  private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    authenticationConverter = mock(CamundaAuthenticationConverter.class);
    holder = mock(CamundaAuthenticationHolder.class);
    authenticationProvider =
        new DefaultCamundaAuthenticationProvider(holder, authenticationConverter);
  }

  @Test
  void shouldReturnAuthenticationFromHolder() {
    // given
    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));

    final var mockAuthentication = mock(Authentication.class);
    when(mockAuthentication.getPrincipal()).thenReturn("foo");
    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    when(holder.get()).thenReturn(expectedAuthentication);

    // when
    final var actualAuthentication = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(actualAuthentication).isNotNull();
    assertThat(actualAuthentication).isEqualTo(expectedAuthentication);
  }

  @Test
  void shouldConvertAndHoldAuthentication() {
    // given
    final var expectedAuthentication = CamundaAuthentication.of(b -> b.user("foo"));

    final var mockAuthentication = mock(Authentication.class);
    when(mockAuthentication.getPrincipal()).thenReturn("foo");
    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    when(authenticationConverter.convert(eq(mockAuthentication)))
        .thenReturn(expectedAuthentication);

    // when
    final var actualAuthentication = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(actualAuthentication).isNotNull();
    assertThat(actualAuthentication).isEqualTo(expectedAuthentication);
    verify(holder).set(eq(expectedAuthentication));
  }

  @Test
  void shouldConvertButNotCacheIfAnonymous() {
    // given
    final var expectedAuthentication = CamundaAuthentication.anonymous();

    final var mockAuthentication = mock(Authentication.class);
    when(mockAuthentication.getPrincipal()).thenReturn("foo");
    SecurityContextHolder.getContext().setAuthentication(mockAuthentication);
    when(authenticationConverter.convert(eq(mockAuthentication)))
        .thenReturn(expectedAuthentication);

    // when
    final var actualAuthentication = authenticationProvider.getCamundaAuthentication();

    // then
<<<<<<< HEAD
    assertThat(actualAuthentication).isNotNull();
    assertThat(actualAuthentication).isEqualTo(expectedAuthentication);
    verify(holder, times(0)).set(eq(expectedAuthentication));
=======
    assertThat(claims).isNotNull();
    assertThat(claims).containsKey("sub");
    assertThat(claims).containsKey("aud");
    assertThat(claims).containsKey("groups");
    assertThat(claims.get("sub")).isEqualTo(sub1);
    assertThat(claims.get("aud")).isEqualTo(aud1);
    assertThat(claims.get("groups")).isEqualTo(List.of("g1", "g2"));
  }

  @Test
  void tokenContainsTenantIdsInAuthenticationContext() {
    // given
    final var username = "test-user";
    final var tenants =
        List.of(
            new TenantDTO(1L, "tenant-1", "Tenant One", "First"),
            new TenantDTO(2L, "tenant-2", "Tenant Two", "Second"));
    final var authenticationContext =
        new AuthenticationContextBuilder().withUsername(username).withTenants(tenants).build();

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", username))),
            null,
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var authContext = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(authContext.authenticatedTenantIds())
        .containsExactlyInAnyOrder("tenant-1", "tenant-2");
    assertThat(authContext.authenticatedUsername()).isEqualTo(username);
  }

  @Test
  void usernameIsSetInAuthenticationWhenOnAuthenticationContext() {
    // given
    final var username = "test-user";
    final var authenticationContext =
        new AuthenticationContextBuilder().withUsername(username).build();

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", username))),
            null,
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var authentication = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(authentication.authenticatedUsername()).isEqualTo(username);
    assertThat(authentication.authenticatedClientId()).isNull();
  }

  @Test
  void clientIdIsSetInAuthenticationWhenOnAuthenticationContext() {
    // given
    final var clientId = "my-application";
    final var authenticationContext =
        new AuthenticationContextBuilder().withClientId(clientId).build();

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", clientId))),
            null,
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var authContext = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(authContext.authenticatedUsername()).isNull();
    assertThat(authContext.authenticatedClientId()).isEqualTo(clientId);
  }

  @Test
  void shouldThrowExceptionWhenNoMatchingConverterFound() {
    // given
    SecurityContextHolder.getContext().setAuthentication(null);

    // when / then
    assertThatThrownBy(() -> authenticationProvider.getCamundaAuthentication())
        .isInstanceOf(CamundaAuthenticationException.class);
  }

  @Test
  void shouldReturnAnonymousCamundaAuthenticationWhenApiProtectionDisabled() {
    // given
    SecurityContextHolder.getContext().setAuthentication(null);
    authenticationConverter = new DefaultCamundaAuthenticationConverter();
    final var unprotectedAuthenticationConverter = new UnprotectedCamundaAuthenticationConverter();
    authenticationProvider =
        new DefaultCamundaAuthenticationProvider(
            new CamundaAuthenticationDelegatingConverter(
                List.of(unprotectedAuthenticationConverter, authenticationConverter)));

    // when
    final var authContext = authenticationProvider.getCamundaAuthentication();

    // then
    assertThat(authContext).isEqualTo(CamundaAuthentication.anonymous());
  }

  private void setJwtAuthenticationInContext(final String sub, final String aud) {
    final Jwt jwt =
        new Jwt(
            JWT.create()
                .withIssuer("issuer1")
                .withAudience(aud)
                .withSubject(sub)
                .sign(Algorithm.none()),
            Instant.ofEpochSecond(10),
            Instant.ofEpochSecond(100),
            Map.of("alg", "RSA256"),
            Map.of("sub", sub, "aud", aud, "groups", List.of("g1", "g2")));

    final AbstractAuthenticationToken token =
        new CamundaJwtAuthenticationToken(
            jwt,
            new CamundaJwtUser(
                jwt,
                new OAuthContext(
                    new HashSet<>(),
                    new AuthenticationContextBuilder()
                        .withUsername(sub)
                        .withGroups(List.of("g1", "g2"))
                        .build())),
            null,
            null);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  private void setOidcAuthenticationInContext(
      final String usernameClaim, final String usernameValue, final String aud) {
    final String tokenValue = "{}";
    final Instant tokenIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final Instant tokenExpiresAt = tokenIssuedAt.plus(1, ChronoUnit.DAYS);

    final var oauth2Authentication =
        new OAuth2AuthenticationToken(
            new CamundaOidcUser(
                new DefaultOidcUser(
                    Collections.emptyList(),
                    new OidcIdToken(
                        tokenValue,
                        tokenIssuedAt,
                        tokenExpiresAt,
                        Map.of("aud", aud, usernameClaim, usernameValue))),
                null,
                Collections.emptySet(),
                new AuthenticationContextBuilder().withUsername(usernameValue).build()),
            List.of(),
            "oidc");

    SecurityContextHolder.getContext().setAuthentication(oauth2Authentication);
  }

  private void setUsernamePasswordAuthenticationInContext(final String username) {
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser().withUsername(username).withPassword("admin").build(),
            null);
    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
>>>>>>> a87007db2b0 (fix: removing `userKey` from tests)
  }
}
