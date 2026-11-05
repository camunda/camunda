/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvVarSecretStoreConfigTest {

  @Test
  void shouldNotRenderTheEnvironment() {
    // given
    final var config =
        new EnvVarSecretStoreConfig(
            "SECRET_",
            Map.of(
                "SECRET_API_KEY", "prefixed-secret-value",
                "DB_PASSWORD", "platform-credential-value"));

    // when
    final var rendered = config.toString();

    // then
    assertThat(rendered)
        .contains("SECRET_")
        .doesNotContain(
            "SECRET_API_KEY", "prefixed-secret-value", "DB_PASSWORD", "platform-credential-value");
  }
}
