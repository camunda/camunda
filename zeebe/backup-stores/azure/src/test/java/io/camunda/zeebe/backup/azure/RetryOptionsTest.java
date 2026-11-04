/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.backup.azure.AzureBackupStoreException.ConfigurationException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RetryOptionsTest {

  @Test
  void shouldApplyMaxAttempts() {
    // given
    final var config = configBuilder().withRetryMaxAttempts(Optional.of(2)).build();

    // when
    final var options = AzureBackupStore.retryOptions(config);

    // then
    assertThat(options).isPresent();
    assertThat(options.get().getMaxTries()).isEqualTo(2);
  }

  @Test
  void shouldKeepSdkDefaultsWhenMaxAttemptsIsNotSet() {
    // when
    final var options = AzureBackupStore.retryOptions(configBuilder().build());

    // then
    assertThat(options).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void shouldRejectNonPositiveMaxAttempts(final int maxAttempts) {
    // given
    final var builder = configBuilder().withRetryMaxAttempts(Optional.of(maxAttempts));

    // when / then
    assertThatThrownBy(builder::build)
        .isInstanceOf(ConfigurationException.class)
        .hasMessageContaining("maxAttempts");
  }

  private static AzureBackupConfig.Builder configBuilder() {
    return new AzureBackupConfig.Builder()
        .withEndpoint("https://localhost")
        .withContainerName("container");
  }
}
