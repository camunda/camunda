/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.authentication.clusteradmin.ClusterAdminAuthenticationConverter;
import io.camunda.authentication.clusteradmin.ClusterAdminBasicSecurityConfiguration;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Verifies the OIDC leak-prevention path: a cluster-admin bearer token with {@link
 * ClusterAdminBasicSecurityConfiguration#CLUSTER_ADMIN_AUTHORITY} is claimed first by {@link
 * ClusterAdminAuthenticationConverter}, which runs at {@code HIGHEST_PRECEDENCE} and returns a
 * client principal with no memberships.
 *
 * <p>This prevents the token from falling through to CSL's {@code OidcTokenAuthenticationConverter}
 * and inheriting memberships from a colliding principal. If the converter is missing from OIDC or
 * ordered incorrectly, CSL would claim the token first and the test would fail.
 */
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=https://redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.jwk-set-uri=jwks.example.com"
    })
class ClusterAdminOidcConverterWiringTest extends AbstractWebSecurityConfigTest {

  private static final String COLLISION = "collision";

  // The real, @Order-sorted converter chain (the mocked CamundaAuthenticationProvider from the base
  // class does not affect these beans).
  @Autowired private List<CamundaAuthenticationConverter<Authentication>> converters;

  @Test
  void shouldResolveClusterAdminBearerWithClusterAdminConverterFirst() {
    // given — a bearer principal that matched the cluster-admin chain (carries the marker
    // authority), whose client id could collide with a DB principal
    final Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(COLLISION).build();
    final Authentication principal =
        new JwtAuthenticationToken(
            jwt,
            List.of(
                new SimpleGrantedAuthority(
                    ClusterAdminBasicSecurityConfiguration.CLUSTER_ADMIN_AUTHORITY)),
            COLLISION);

    // when — the converter chain dispatches to the first converter that claims the principal
    final CamundaAuthenticationConverter<Authentication> winner =
        converters.stream().filter(c -> c.supports(principal)).findFirst().orElseThrow();

    // then — it is our converter (registered under OIDC and ordered ahead of CSL's DB-backed one),
    // and it produces a membership-free client principal — no colliding memberships attached
    assertThat(winner).isInstanceOf(ClusterAdminAuthenticationConverter.class);

    final CamundaAuthentication authentication = winner.convert(principal);
    assertThat(authentication.authenticatedClientId()).isEqualTo(COLLISION);
    assertThat(authentication.authenticatedUsername()).isNull();
    assertThat(authentication.authenticatedGroupIds()).isEmpty();
    assertThat(authentication.authenticatedRoleIds()).isEmpty();
    assertThat(authentication.authenticatedTenantIds()).isEmpty();
    assertThat(authentication.authenticatedMappingRuleIds()).isEmpty();
  }
}
