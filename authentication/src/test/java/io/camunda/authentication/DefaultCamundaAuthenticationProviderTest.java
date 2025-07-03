/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

public class DefaultCamundaAuthenticationProviderTest {

  //  @Mock private RequestAttributes requestAttributes;
  //  private CamundaAuthenticationProvider authenticationProvider;
  //  private CamundaAuthenticationConverter<Authentication> authenticationConverter;
  //
  //  @BeforeEach
  //  void setup() throws Exception {
  //    MockitoAnnotations.openMocks(this).close();
  //    RequestContextHolder.setRequestAttributes(requestAttributes);
  //    authenticationConverter = new CamundaAuthenticationConverter();
  //    authenticationProvider = new CamundaAuthenticationProvider(authenticationConverter);
  //  }
  //
  //  @Test
  //  void tokenContainsUsernameWithBasicAuth() {
  //
  //    // given
  //    final var username = "username";
  //    setUsernamePasswordAuthenticationInContext(username);
  //
  //    // when
  //    final var claims = authenticationProvider.getCamundaAuthentication().attributes();
  //
  //    // then
  //    assertNotNull(claims);
  //    assertThat(claims).containsKey(Authorization.AUTHORIZED_USERNAME);
  //    assertThat(claims.get(Authorization.AUTHORIZED_USERNAME)).isEqualTo(username);
  //  }
  //
  //  @Test
  //  void tokenContainsExtraClaimsWithOidcAuth() {
  //    // given
  //    final String usernameClaim = "sub";
  //    final String usernameValue = "test-user";
  //    setOidcAuthenticationInContext(usernameClaim, usernameValue, "aud1");
  //
  //    // when
  //    final var authenticatedUsername =
  //        authenticationProvider.getCamundaAuthentication().authenticatedUsername();
  //
  //    // then
  //    assertThat(authenticatedUsername).isEqualTo(usernameValue);
  //  }
  //
  //  @Test
  //  void tokenContainsExtraClaimsWithJwtAuth() {
  //
  //    // given
  //    final String sub1 = "sub1";
  //    final String aud1 = "aud1";
  //    setJwtAuthenticationInContext(sub1, aud1);
  //
  //    // when
  //    final var claims =
  //        (Map<String, Object>)
  //
  // authenticationProvider.getCamundaAuthentication().attributes().get(USER_TOKEN_CLAIMS);
  //
  //    // then
  //    assertNotNull(claims);
  //    assertThat(claims).containsKey("sub");
  //    assertThat(claims).containsKey("aud");
  //    assertThat(claims).containsKey("groups");
  //    assertThat(claims.get("sub")).isEqualTo(sub1);
  //    assertThat(claims.get("aud")).isEqualTo(aud1);
  //    assertThat(claims.get("groups")).isEqualTo(List.of("g1", "g2"));
  //  }
  //
  //  @Test
  //  void tokenContainsTenantIdsInAuthenticationContext() {
  //    // given
  //    final var username = "test-user";
  //    final var tenants =
  //        List.of(
  //            new TenantDTO(1L, "tenant-1", "Tenant One", "First"),
  //            new TenantDTO(2L, "tenant-2", "Tenant Two", "Second"));
  //    final var authenticationContext =
  //        new AuthenticationContextBuilder().withUsername(username).withTenants(tenants).build();
  //
  //    final var principal =
  //        new CamundaOidcUser(
  //            new DefaultOidcUser(
  //                Collections.emptyList(),
  //                new OidcIdToken(
  //                    "tokenValue",
  //                    Instant.now(),
  //                    Instant.now().plusSeconds(3600),
  //                    Map.of("sub", username))),
  //            Collections.emptySet(),
  //            authenticationContext);
  //
  //    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
  //    SecurityContextHolder.getContext().setAuthentication(auth);
  //
  //    // when
  //    final var authContext = authenticationProvider.getCamundaAuthentication();
  //
  //    // then
  //    assertThat(authContext.authenticatedTenantIds())
  //        .containsExactlyInAnyOrder("tenant-1", "tenant-2");
  //    assertThat(authContext.authenticatedUsername()).isEqualTo(username);
  //  }
  //
  //  @Test
  //  void usernameIsSetInAuthenticationWhenOnAuthenticationContext() {
  //    // given
  //    final var username = "test-user";
  //    final var authenticationContext =
  //        new AuthenticationContextBuilder().withUsername(username).build();
  //
  //    final var principal =
  //        new CamundaOidcUser(
  //            new DefaultOidcUser(
  //                Collections.emptyList(),
  //                new OidcIdToken(
  //                    "tokenValue",
  //                    Instant.now(),
  //                    Instant.now().plusSeconds(3600),
  //                    Map.of("sub", username))),
  //            Collections.emptySet(),
  //            authenticationContext);
  //
  //    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
  //    SecurityContextHolder.getContext().setAuthentication(auth);
  //
  //    // when
  //    final var authentication = authenticationProvider.getCamundaAuthentication();
  //
  //    // then
  //    assertThat(authentication.authenticatedUsername()).isEqualTo(username);
  //    assertThat(authentication.authenticatedClientId()).isNull();
  //  }
  //
  //  @Test
  //  void clientIdIsSetInAuthenticationWhenOnAuthenticationContext() {
  //    // given
  //    final var clientId = "my-application";
  //    final var authenticationContext =
  //        new AuthenticationContextBuilder().withClientId(clientId).build();
  //
  //    final var principal =
  //        new CamundaOidcUser(
  //            new DefaultOidcUser(
  //                Collections.emptyList(),
  //                new OidcIdToken(
  //                    "tokenValue",
  //                    Instant.now(),
  //                    Instant.now().plusSeconds(3600),
  //                    Map.of("sub", clientId))),
  //            Collections.emptySet(),
  //            authenticationContext);
  //
  //    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
  //    SecurityContextHolder.getContext().setAuthentication(auth);
  //
  //    // when
  //    final var authContext = authenticationProvider.getCamundaAuthentication();
  //
  //    // then
  //    assertThat(authContext.authenticatedUsername()).isNull();
  //    assertThat(authContext.authenticatedClientId()).isEqualTo(clientId);
  //  }
  //
  //  private void setJwtAuthenticationInContext(final String sub, final String aud) {
  //    final Jwt jwt =
  //        new Jwt(
  //            JWT.create()
  //                .withIssuer("issuer1")
  //                .withAudience(aud)
  //                .withSubject(sub)
  //                .sign(Algorithm.none()),
  //            Instant.ofEpochSecond(10),
  //            Instant.ofEpochSecond(100),
  //            Map.of("alg", "RSA256"),
  //            Map.of("sub", sub, "aud", aud, "groups", List.of("g1", "g2")));
  //
  //    final AbstractAuthenticationToken token =
  //        new CamundaJwtAuthenticationToken(
  //            jwt,
  //            new CamundaJwtUser(
  //                jwt,
  //                new OAuthContext(
  //                    new HashSet<>(),
  //                    new AuthenticationContextBuilder()
  //                        .withUsername(sub)
  //                        .withGroups(List.of("g1", "g2"))
  //                        .build())),
  //            null,
  //            null);
  //    SecurityContextHolder.getContext().setAuthentication(token);
  //  }
  //
  //  private void setOidcAuthenticationInContext(
  //      final String usernameClaim, final String usernameValue, final String aud) {
  //    final String tokenValue = "{}";
  //    final Instant tokenIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  //    final Instant tokenExpiresAt = tokenIssuedAt.plus(1, ChronoUnit.DAYS);
  //
  //    final var oauth2Authentication =
  //        new OAuth2AuthenticationToken(
  //            new CamundaOidcUser(
  //                new DefaultOidcUser(
  //                    Collections.emptyList(),
  //                    new OidcIdToken(
  //                        tokenValue,
  //                        tokenIssuedAt,
  //                        tokenExpiresAt,
  //                        Map.of("aud", aud, usernameClaim, usernameValue))),
  //                Collections.emptySet(),
  //                new AuthenticationContextBuilder().withUsername(usernameValue).build()),
  //            List.of(),
  //            "oidc");
  //
  //    SecurityContextHolder.getContext().setAuthentication(oauth2Authentication);
  //  }
  //
  //  private void setUsernamePasswordAuthenticationInContext(final String username) {
  //    final UsernamePasswordAuthenticationToken authenticationToken =
  //        new UsernamePasswordAuthenticationToken(
  //            CamundaUserBuilder.aCamundaUser()
  //                .withUsername(username)
  //                .withPassword("admin")
  //                .withUserKey(1L)
  //                .build(),
  //            null);
  //    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  //  }
}
