/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class DefaultAuthenticationInitializerTest {

  private static final String CONSOLIDATED_AUTH = Profile.CONSOLIDATED_AUTH.getId();
  private static final String AUTHENTICATION_METHOD = "camunda.security.authentication.method";

  @Test
  void shouldAddConsolidatedAuthWhenWebappProfileIsActive() {
    // given
    final var context = contextWith(Map.of(), Profile.BROKER, Profile.OPERATE);

    // when
    new DefaultAuthenticationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getActiveProfiles()).contains(CONSOLIDATED_AUTH);
  }

  @Test
  void shouldAddConsolidatedAuthWhenWebappProfileIsActiveAndAuthenticationMethodIsSet() {
    // given
    final var context =
        contextWith(
            Map.of(AUTHENTICATION_METHOD, "basic"),
            Profile.BROKER,
            Profile.OPERATE,
            Profile.TASKLIST,
            Profile.IDENTITY);

    // when
    new DefaultAuthenticationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getActiveProfiles()).contains(CONSOLIDATED_AUTH);
  }

  @Test
  void shouldAddConsolidatedAuthWhenOnlyBrokerProfileIsActive() {
    // given — the embedded gateway serves the REST API by default
    final var context = contextWith(Map.of(), Profile.BROKER);

    // when
    new DefaultAuthenticationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getActiveProfiles()).contains(CONSOLIDATED_AUTH);
  }

  @Test
  void shouldAddConsolidatedAuthWhenOnlyGatewayProfileIsActive() {
    // given
    final var context = contextWith(Map.of(AUTHENTICATION_METHOD, "oidc"), Profile.GATEWAY);

    // when
    new DefaultAuthenticationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getActiveProfiles()).contains(CONSOLIDATED_AUTH);
  }

  @Test
  void shouldNotAddConsolidatedAuthWhenBrokerServesNoHttpApi() {
    // given — a broker whose embedded gateway is disabled serves no HTTP API
    final var context =
        contextWith(
            Map.of("zeebe.broker.gateway.enable", "false", AUTHENTICATION_METHOD, "oidc"),
            Profile.BROKER,
            Profile.STANDALONE);

    // when
    new DefaultAuthenticationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getActiveProfiles()).doesNotContain(CONSOLIDATED_AUTH);
  }

  @Test
  void shouldKeepConsolidatedAuthWhenAlreadyActive() {
    // given
    final var context =
        contextWith(
            Map.of(AUTHENTICATION_METHOD, "basic"), Profile.BROKER, Profile.CONSOLIDATED_AUTH);

    // when
    new DefaultAuthenticationInitializer().initialize(context);

    // then
    assertThat(context.getEnvironment().getActiveProfiles())
        .containsExactlyInAnyOrder(Profile.BROKER.getId(), CONSOLIDATED_AUTH);
  }

  private static GenericApplicationContext contextWith(
      final Map<String, Object> properties, final Profile... profiles) {
    final var context = new GenericApplicationContext();
    final var environment = context.getEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    final var profileIds = new String[profiles.length];
    for (int i = 0; i < profiles.length; i++) {
      profileIds[i] = profiles[i].getId();
    }
    environment.setActiveProfiles(profileIds);
    return context;
  }
}
