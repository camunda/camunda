/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.clusteradmin;

import static io.camunda.authentication.clusteradmin.ClusterAdminBasicSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ClusterAdminJwtAuthenticationConverterTest {

  private static final String CLIENT_ID_CLAIM = "client_id";
  private static final String GROUPS_CLAIM = "groups";

  private static ClusterAdminJwtAuthenticationConverter converter(final Environment env) {
    final var props =
        ClusterAdminOidcProperties.loadAndValidate(env, CLIENT_ID_CLAIM, GROUPS_CLAIM);
    return new ClusterAdminJwtAuthenticationConverter(props, CLIENT_ID_CLAIM, GROUPS_CLAIM);
  }

  @Test
  void shouldGrantWhenClientIdMatches() {
    // given
    final var converter = converter(env(Map.of("...oidc.clients[0]", "ops-client")));

    // when
    final JwtAuthenticationToken token =
        (JwtAuthenticationToken) converter.convert(jwt(Map.of("client_id", "ops-client")));

    // then
    assertThat(authorities(token)).contains(CLUSTER_ADMIN_AUTHORITY);
    assertThat(token.getName()).isEqualTo("ops-client");
  }

  @Test
  void shouldGrantWhenGroupMatches() {
    // given
    final var converter = converter(env(Map.of("...oidc.groups[0]", "camunda-admins")));

    // when — group matches even though the client id is not configured
    final JwtAuthenticationToken token =
        (JwtAuthenticationToken)
            converter.convert(
                jwt(Map.of("client_id", "some-client", "groups", List.of("camunda-admins"))));

    // then
    assertThat(authorities(token)).contains(CLUSTER_ADMIN_AUTHORITY);
    assertThat(token.getName()).isEqualTo("some-client");
  }

  @Test
  void shouldGrantWhenGenericClaimMatches() {
    // given
    final var converter =
        converter(
            env(
                Map.of(
                    "...oidc.claims[0].name", "roles",
                    "...oidc.claims[0].value", "cluster-admin")));

    // when
    final JwtAuthenticationToken token =
        (JwtAuthenticationToken)
            converter.convert(jwt(Map.of("sub", "svc", "roles", List.of("cluster-admin"))));

    // then
    assertThat(authorities(token)).contains(CLUSTER_ADMIN_AUTHORITY);
  }

  @Test
  void shouldNotGrantWhenNothingMatches() {
    // given
    final var converter =
        converter(
            env(
                Map.of(
                    "...oidc.clients[0]", "ops-client",
                    "...oidc.groups[0]", "camunda-admins",
                    "...oidc.claims[0].name", "roles",
                    "...oidc.claims[0].value", "cluster-admin")));

    // when — a valid token that matches no configured client, group, or claim
    final JwtAuthenticationToken token =
        (JwtAuthenticationToken)
            converter.convert(
                jwt(
                    Map.of(
                        "client_id", "stranger",
                        "groups", List.of("other-group"),
                        "roles", List.of("reader"))));

    // then — authenticated, but no cluster-admin authority (chain will 403, not 401)
    assertThat(token.isAuthenticated()).isTrue();
    assertThat(authorities(token)).doesNotContain(CLUSTER_ADMIN_AUTHORITY);
    assertThat(token.getName()).isEqualTo("stranger");
  }

  @Test
  void shouldFallBackToSubjectForNameWhenNoClientId() {
    // given — only a generic claim matcher; no client-id-claim needed
    final var converter =
        converter(
            env(
                Map.of(
                    "...oidc.claims[0].name", "roles",
                    "...oidc.claims[0].value", "cluster-admin")));

    // when — token has no client_id claim, matched by group-less generic claim
    final JwtAuthenticationToken token =
        (JwtAuthenticationToken)
            converter.convert(
                jwt(Map.of("sub", "service-account", "roles", List.of("cluster-admin"))));

    // then — name falls back to the subject
    assertThat(authorities(token)).contains(CLUSTER_ADMIN_AUTHORITY);
    assertThat(token.getName()).isEqualTo("service-account");
  }

  @Test
  void shouldGrantWhenClientIdClaimUnconfiguredAndGenericClaimMatches() {
    // given — only a generic claim matcher and NO provider client-id-claim (null); the converter
    // must not fail trying to read a client id it was never told how to find
    final var props =
        ClusterAdminOidcProperties.loadAndValidate(
            env(
                Map.of(
                    "...oidc.claims[0].name", "roles",
                    "...oidc.claims[0].value", "cluster-admin")),
            null,
            GROUPS_CLAIM);
    final var converter = new ClusterAdminJwtAuthenticationConverter(props, null, GROUPS_CLAIM);

    // when — a token carrying the matching generic claim but no client id
    final JwtAuthenticationToken token =
        (JwtAuthenticationToken)
            converter.convert(jwt(Map.of("sub", "svc", "roles", List.of("cluster-admin"))));

    // then — granted via the generic claim; name falls back to the subject
    assertThat(authorities(token)).contains(CLUSTER_ADMIN_AUTHORITY);
    assertThat(token.getName()).isEqualTo("svc");
  }

  private static List<String> authorities(final JwtAuthenticationToken token) {
    return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }

  private static Jwt jwt(final Map<String, Object> claims) {
    return Jwt.withTokenValue("token").header("alg", "none").claims(c -> c.putAll(claims)).build();
  }

  private static MockEnvironment env(final Map<String, String> properties) {
    final MockEnvironment env = new MockEnvironment();
    properties.forEach(
        (k, v) -> env.setProperty(k.replace("...", "camunda.security.cluster-admin."), v));
    return env;
  }
}
