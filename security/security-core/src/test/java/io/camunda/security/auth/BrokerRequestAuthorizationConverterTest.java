/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth;

import static io.camunda.security.api.model.config.AuthenticationMethod.OIDC;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_ANONYMOUS_USER;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_CLIENT_ID;
import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static io.camunda.zeebe.auth.Authorization.USER_GROUPS_CLAIMS;
import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIMS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class BrokerRequestAuthorizationConverterTest {

  private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_~@.+-]+$");
  private static final Pattern GROUP_ID_PATTERN = Pattern.compile(".*");

  private static EngineSecurityConfig defaultConfig() {
    return new EngineSecurityConfig(
        new AuthenticationConfiguration(),
        /* authorizationsEnabled= */ true,
        /* multiTenancyChecksEnabled= */ false,
        new InitializationConfiguration(),
        ID_PATTERN,
        GROUP_ID_PATTERN);
  }

  @Test
  void shouldOnlyContainAuthenticationClaimsWhenAuthorizationAndMultiTenancyDisabled() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("foo"));
    final var config =
        new EngineSecurityConfig(
            new AuthenticationConfiguration(),
            /* authorizationsEnabled= */ false,
            /* multiTenancyChecksEnabled= */ false,
            new InitializationConfiguration(),
            ID_PATTERN,
            GROUP_ID_PATTERN);
    final var converter = new BrokerRequestAuthorizationConverter(config);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then — identity claims are present (for audit logging), but no authorization claims
    assertThat(brokerRequestAuth).hasSize(1);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_USERNAME, "foo");
    assertThat(brokerRequestAuth).doesNotContainKey(USER_TOKEN_CLAIMS);
    assertThat(brokerRequestAuth).doesNotContainKey(USER_GROUPS_CLAIMS);
  }

  @Test
  void shouldContainAnonymousClaim() {
    // given
    final var authentication = CamundaAuthentication.anonymous();
    final var converter = new BrokerRequestAuthorizationConverter(defaultConfig());

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(1);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_ANONYMOUS_USER, true);
  }

  @Test
  void shouldContainUsername() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.user("foo"));
    final var converter = new BrokerRequestAuthorizationConverter(defaultConfig());

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(1);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_USERNAME, "foo");
  }

  @Test
  void shouldContainClientID() {
    // given
    final var authentication = CamundaAuthentication.of(b -> b.clientId("foo"));
    final var auth = new AuthenticationConfiguration();
    auth.setMethod(OIDC);
    final var config =
        new EngineSecurityConfig(
            auth,
            /* authorizationsEnabled= */ true,
            /* multiTenancyChecksEnabled= */ false,
            new InitializationConfiguration(),
            ID_PATTERN,
            GROUP_ID_PATTERN);
    final var converter = new BrokerRequestAuthorizationConverter(config);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(1);
    assertThat(brokerRequestAuth).containsEntry(AUTHORIZED_CLIENT_ID, "foo");
  }

  @Test
  void shouldContainTokenClaims() {
    // given
    final Map<String, Object> claims = Map.of("sub", "foo");
    final var authentication = CamundaAuthentication.of(b -> b.claims(claims));
    final var auth = new AuthenticationConfiguration();
    auth.setMethod(OIDC);
    final var config =
        new EngineSecurityConfig(
            auth,
            /* authorizationsEnabled= */ true,
            /* multiTenancyChecksEnabled= */ false,
            new InitializationConfiguration(),
            ID_PATTERN,
            GROUP_ID_PATTERN);
    final var converter = new BrokerRequestAuthorizationConverter(config);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(1);
    assertThat(brokerRequestAuth).containsEntry(USER_TOKEN_CLAIMS, claims);
  }

  @Test
  void shouldContainGroupsIfClaimConfigured() {
    // given
    final var groups = List.of("group1", "group2");
    final var authentication = CamundaAuthentication.of(b -> b.groupIds(groups));

    final var oidcConfiguration = new OidcConfiguration();
    oidcConfiguration.setGroupsClaim("groups");
    final var auth = new AuthenticationConfiguration();
    auth.setOidc(oidcConfiguration);
    auth.setMethod(OIDC);
    final var config =
        new EngineSecurityConfig(
            auth,
            /* authorizationsEnabled= */ true,
            /* multiTenancyChecksEnabled= */ false,
            new InitializationConfiguration(),
            ID_PATTERN,
            GROUP_ID_PATTERN);
    final var converter = new BrokerRequestAuthorizationConverter(config);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(1);
    assertThat(brokerRequestAuth).containsEntry(USER_GROUPS_CLAIMS, groups);
  }

  @Test
  void shouldIgnoreGroupsIfAuthenticationMethodNotOIDC() {
    // given
    final var groups = List.of("group1", "group2");
    final var authentication = CamundaAuthentication.of(b -> b.groupIds(groups));

    final var oidcConfiguration = new OidcConfiguration();
    oidcConfiguration.setGroupsClaim("groups");
    final var auth = new AuthenticationConfiguration();
    auth.setOidc(oidcConfiguration);
    final var config =
        new EngineSecurityConfig(
            auth,
            /* authorizationsEnabled= */ true,
            /* multiTenancyChecksEnabled= */ false,
            new InitializationConfiguration(),
            ID_PATTERN,
            GROUP_ID_PATTERN);
    final var converter = new BrokerRequestAuthorizationConverter(config);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(0);
  }

  @Test
  void shouldIgnoreGroupsIfClaimNotConfigured() {
    // given
    final var groups = List.of("group1", "group2");
    final var authentication = CamundaAuthentication.of(b -> b.groupIds(groups));

    final var auth = new AuthenticationConfiguration();
    auth.setMethod(OIDC);
    final var config =
        new EngineSecurityConfig(
            auth,
            /* authorizationsEnabled= */ true,
            /* multiTenancyChecksEnabled= */ false,
            new InitializationConfiguration(),
            ID_PATTERN,
            GROUP_ID_PATTERN);
    final var converter = new BrokerRequestAuthorizationConverter(config);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(brokerRequestAuth).hasSize(0);
  }

  @Test
  void shouldNotInvokeGroupsSupplierWhenNoGroupsClaimConfigured() {
    // given — OIDC with authorizations enabled and no groupsClaim configured. Groups would
    // otherwise come from a DB lookup, which we don't want on the broker request path; the engine
    // resolves groups from its own membershipState in that case. (Note: with OIDC and no
    // groupsClaim,
    // SecurityConfiguration derives camundaGroupsEnabled=true; the converter's gate
    // !camundaGroupsEnabled && groupsClaimConfigured therefore short-circuits on
    // groupsClaimConfigured.)
    final var invoked = new AtomicBoolean();
    final var oidcConfiguration = new OidcConfiguration();
    // no groupsClaim set
    final var securityConfiguration = new SecurityConfiguration();
    securityConfiguration.getAuthentication().setMethod(OIDC);
    securityConfiguration.getAuthentication().setOidc(oidcConfiguration);
    // Authorizations explicitly enabled so shouldIncludeAuthorizationClaims is true; the gate
    // under test (groupsClaimConfigured) is what must keep the supplier from firing.
    securityConfiguration.getAuthorizations().setEnabled(true);

    final var authentication =
        CamundaAuthentication.of(
            b ->
                b.user("foo")
                    .groupIdsSupplier(
                        () -> {
                          invoked.set(true);
                          return List.of("group1");
                        }));
    final var converter = new BrokerRequestAuthorizationConverter(securityConfiguration);

    // when
    final var brokerRequestAuth = converter.convert(authentication);

    // then
    assertThat(invoked).isFalse();
    assertThat(brokerRequestAuth).doesNotContainKey(USER_GROUPS_CLAIMS);
  }
}
