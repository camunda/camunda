/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.shared.ConfigSanitizingFunction.ConfigSanitizingProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SanitizableData;

final class ConfigSanitizingFunctionTest {

  private static final List<String> PRODUCTION_KEYWORDS =
      List.of(
          "user",
          "pass",
          "secret",
          "license",
          "accessKey",
          "accountKey",
          "token",
          "connectionString");

  private final ConfigSanitizingFunction function =
      new ConfigSanitizingFunction(new ConfigSanitizingProperties(PRODUCTION_KEYWORDS));

  @Test
  void shouldSanitizeLicenseKey() {
    // given
    final var data = new SanitizableData(null, "camunda.license.key", "my-secret-license");

    // when
    final var result = function.apply(data);

    // then
    assertThat(result.getValue()).isEqualTo(SanitizableData.SANITIZED_VALUE);
  }

  @Test
  void shouldNotSanitizeUnrelatedProperty() {
    // given
    final var data = new SanitizableData(null, "server.port", "8080");

    // when
    final var result = function.apply(data);

    // then
    assertThat(result.getValue()).isEqualTo("8080");
  }

  @Test
  void shouldSanitizeCaseInsensitively() {
    // given
    final var data = new SanitizableData(null, "CAMUNDA_LICENSE_KEY", "my-secret-license");

    // when
    final var result = function.apply(data);

    // then
    assertThat(result.getValue()).isEqualTo(SanitizableData.SANITIZED_VALUE);
  }

  @Test
  void shouldNotSanitizeWhenValueIsNull() {
    // given
    final var data = new SanitizableData(null, "camunda.license.key", null);

    // when
    final var result = function.apply(data);

    // then
    assertThat(result.getValue()).isNull();
  }
}
