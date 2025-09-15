/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {

  SecurityConfiguration securityConfiguration = new SecurityConfiguration();

  @Test
  void shouldAcceptIdWithTilde() {
    // given
    final String pattern = securityConfiguration.getIdValidationPattern();

    // when
    final String idWithSpecialCharTilde = "id_with_special_char~";

    // then
    assertThat(idWithSpecialCharTilde.matches(pattern)).isTrue();
  }

  @Test
  void shouldDenyIdWithExclamationMark() {
    // given
    final String pattern = securityConfiguration.getIdValidationPattern();

    // when
    final String idWithNotAllowedSpecialChar = "id_with_not_allowed_special_char!";

    // then
    assertThat(idWithNotAllowedSpecialChar.matches(pattern)).isFalse();
  }
}
