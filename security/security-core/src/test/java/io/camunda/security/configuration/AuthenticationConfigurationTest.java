/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.AuthenticationMethod;
import org.junit.jupiter.api.Test;

class AuthenticationConfigurationTest {

  @Test
  void shouldEnableCamundaGroupsAndUsersInBasicAuth() {
    // given:
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.BASIC);

    // expect:
    assertThat(config.isCamundaUsersEnabled()).isTrue();
    assertThat(config.isCamundaGroupsEnabled()).isTrue();
  }

  @Test
  void shouldEnableOnlyCamundaGroupsInOIDCAuth() {
    // given:
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.OIDC);

    // expect:
    assertThat(config.isCamundaUsersEnabled()).isFalse();
    assertThat(config.isCamundaGroupsEnabled()).isTrue();
  }

  @Test
  void shouldDisableCamundaGroupsAndUsersInOICDAuthWithGroupsClaim() {
    // given:
    final var config = new AuthenticationConfiguration();
    config.setMethod(AuthenticationMethod.OIDC);
    config.getOidc().setGroupsClaim("groups");

    // expect:
    assertThat(config.isCamundaUsersEnabled()).isFalse();
    assertThat(config.isCamundaGroupsEnabled()).isFalse();
  }
}
