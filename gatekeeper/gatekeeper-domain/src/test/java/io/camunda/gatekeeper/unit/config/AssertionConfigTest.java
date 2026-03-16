/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.config.AssertionConfig;
import io.camunda.gatekeeper.config.AssertionConfig.KidCase;
import io.camunda.gatekeeper.config.AssertionConfig.KidDigestAlgorithm;
import io.camunda.gatekeeper.config.AssertionConfig.KidEncoding;
import io.camunda.gatekeeper.config.AssertionConfig.KidSource;
import org.junit.jupiter.api.Test;

final class AssertionConfigTest {

  @Test
  void defaultsAreAppliedForNullEnums() {
    final var config =
        new AssertionConfig("/path", "pass", "alias", "keypass", null, null, null, null);

    assertThat(config.kidSource()).isEqualTo(KidSource.PUBLIC_KEY);
    assertThat(config.kidDigestAlgorithm()).isEqualTo(KidDigestAlgorithm.SHA256);
    assertThat(config.kidEncoding()).isEqualTo(KidEncoding.BASE64URL);
  }

  @Test
  void validateThrowsWhenKidCaseSetWithNonHexEncoding() {
    final var config =
        new AssertionConfig(
            "/path", "pass", "alias", "keypass", null, null, KidEncoding.BASE64URL, KidCase.UPPER);

    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("kidCase can only be set when kidEncoding is HEX");
  }

  @Test
  void validatePassesWhenKidCaseSetWithHexEncoding() {
    final var config =
        new AssertionConfig(
            "/path", "pass", "alias", "keypass", null, null, KidEncoding.HEX, KidCase.UPPER);

    config.validate(); // should not throw
  }

  @Test
  void isConfiguredReturnsFalseForNullPath() {
    final var config = new AssertionConfig(null, null, null, null, null, null, null, null);

    assertThat(config.isConfigured()).isFalse();
  }

  @Test
  void isConfiguredReturnsFalseForBlankPath() {
    final var config = new AssertionConfig("   ", null, null, null, null, null, null, null);

    assertThat(config.isConfigured()).isFalse();
  }

  @Test
  void isConfiguredReturnsTrueForNonBlankPath() {
    final var config =
        new AssertionConfig("/keystore.p12", "pass", "alias", "keypass", null, null, null, null);

    assertThat(config.isConfigured()).isTrue();
  }
}
