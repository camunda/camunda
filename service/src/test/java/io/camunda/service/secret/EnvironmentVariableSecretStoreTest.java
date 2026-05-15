/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentVariableSecretStoreTest {

  @Test
  void shouldReturnValueWhenPresent() {
    final var env = Map.of("MY_TOKEN", "sk-abc123");
    final var store = new EnvironmentVariableSecretStore(env::get);

    assertThat(store.resolve("MY_TOKEN")).contains("sk-abc123");
  }

  @Test
  void shouldReturnEmptyWhenAbsent() {
    final var store = new EnvironmentVariableSecretStore(name -> null);

    assertThat(store.resolve("UNKNOWN")).isEmpty();
  }

  @Test
  void shouldExposeIdAsEnv() {
    assertThat(new EnvironmentVariableSecretStore().id()).isEqualTo("env");
  }
}
