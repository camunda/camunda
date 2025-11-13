/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.security.entity.AuthenticationMethod.OIDC;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.IS_CAMUNDA_GROUPS_ENABLED;
import static io.camunda.zeebe.auth.Authorization.IS_CAMUNDA_USERS_ENABLED;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BrokerRequestAuthorizationConverterTest {

  @Test
  void shouldContainAnonymousClaim() {
    // given
    final var authentication = CamundaAuthentication.anonymous();
    final var converter = new BrokerRequestAuthorizationConverter(new SecurityConfiguration());

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(3);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_ANONYMOUS_USER, true);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, false);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, false);
  }

  @Test
  void shouldContainUsername() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("foo"));
    final var converter = new BrokerRequestAuthorizationConverter(new SecurityConfiguration());

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(3);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_USERNAME, "foo");
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, true);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, true);
  }

  @Test
  void shouldContainClientID() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.clientId("foo"));
    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setMethod(OIDC);
    final var converter = new BrokerRequestAuthorizationConverter(securityConfiguration);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(3);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_CLIENT_ID, "foo");
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, true);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, false);
  }

  @Test
  void shouldContainTokenClaims() {
    // given
    final Map<String, Object> claims = Map.of("sub", "foo");
    final var authentication = CamundaAuthentication.of(b -> b.claims(claims));
    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setMethod(OIDC);
    final var converter = new BrokerRequestAuthorizationConverter(securityConfiguration);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(3);
    assertThat(brokerRequestAuth).containsEntry(USER_TOKEN_CLAIMS, claims);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, true);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, false);
  }

  @Test
  void shouldContainGroupsIfClaimConfigured() {
    // given
    final var groups = List.of("group1", "group2");
    final var authentication = CamundaAuthentication.of(b -> b.groupIds(groups));

    final var oidcConfiguration = new OidcAuthenticationConfiguration();
    oidcConfiguration.setGroupsClaim("groups");
    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setOidc(oidcConfiguration);
    securityConfiguration.getAuthentication().setMethod(OIDC);

    final var converter = new BrokerRequestAuthorizationConverter(securityConfiguration);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(3);
    assertThat(brokerRequestAuth).containsEntry(USER_GROUPS_CLAIMS, groups);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, false);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, false);
  }

  @Test
  void shouldIgnoreGroupsIfAuthenticationMethodNotOIDC() {
    // given
    final var groups = List.of("group1", "group2");
    final var authentication = CamundaAuthentication.of(b -> b.groupIds(groups));

    final var oidcConfiguration = new OidcAuthenticationConfiguration();
    oidcConfiguration.setGroupsClaim("groups");
    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setOidc(oidcConfiguration);

    final var converter = new BrokerRequestAuthorizationConverter(securityConfiguration);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(2);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, true);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, true);
  }

  @Test
  void shouldIgnoreGroupsIfClaimNotConfigured() {
    // given
    final var groups = List.of("group1", "group2");
    final var authentication = CamundaAuthentication.of(b -> b.groupIds(groups));

    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setMethod(OIDC);
    final var converter = new BrokerRequestAuthorizationConverter(securityConfiguration);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(2);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_GROUPS_ENABLED, true);
    assertThat(brokerRequestAuth).containsEntry(IS_CAMUNDA_USERS_ENABLED, false);
  }
}
