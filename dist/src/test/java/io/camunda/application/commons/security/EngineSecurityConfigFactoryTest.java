/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Security;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import org.junit.jupiter.api.Test;

class EngineSecurityConfigFactoryTest {

  @Test
  void shouldCopyEverySettingOfTheTenant() {
    // given a tenant whose settings all differ from the defaults
    final var security = new Security();
    security.getAuthentication().setMethod(AuthenticationMethod.OIDC);
    security.getAuthorizations().setEnabled(false);
    security.getMultiTenancy().setChecksEnabled(true);
    final var initialization = new InitializationConfiguration();
    security.setInitialization(initialization);
    security.setIdValidationPattern("^[a-z]+$");

    // when
    final var config = EngineSecurityConfigFactory.create(security);

    // then
    assertThat(config.getAuthentication()).isSameAs(security.getAuthentication());
    assertThat(config.getAuthentication().getMethod()).isEqualTo(AuthenticationMethod.OIDC);
    assertThat(config.isAuthorizationsEnabled()).isFalse();
    assertThat(config.isMultiTenancyChecksEnabled()).isTrue();
    assertThat(config.getInitialization()).isSameAs(initialization);
    assertThat(config.getIdValidationPattern().pattern()).isEqualTo("^[a-z]+$");
    assertThat(config.getGroupIdValidationPattern())
        .isEqualTo(security.getCompiledGroupIdValidationPattern());
  }
}
