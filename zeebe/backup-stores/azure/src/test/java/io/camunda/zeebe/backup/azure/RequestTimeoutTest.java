/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.azure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

final class RequestTimeoutTest {

  /**
   * {@code HttpClientOptions} resolves every timeout it was not given to this azure-core default.
   */
  private static final Duration AZURE_DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  @Test
  void shouldApplyReadTimeoutToResponseAndReadTimeouts() {
    // given
    final var readTimeout = Duration.ofSeconds(90);

    // when
    final var options =
        AzureBackupStore.httpClientOptions(
            configWith(builder -> builder.withReadTimeout(readTimeout)));

    // then
    assertThat(options).isPresent();
    assertThat(options.get().getResponseTimeout()).isEqualTo(readTimeout);
    assertThat(options.get().getReadTimeout()).isEqualTo(readTimeout);
    // an unset write timeout leaves the azure-core default in place
    assertThat(options.get().getWriteTimeout()).isEqualTo(AZURE_DEFAULT_TIMEOUT);
  }

  @Test
  void shouldApplyWriteTimeoutOnly() {
    // given
    final var writeTimeout = Duration.ofSeconds(120);

    // when
    final var options =
        AzureBackupStore.httpClientOptions(
            configWith(builder -> builder.withWriteTimeout(writeTimeout)));

    // then
    assertThat(options).isPresent();
    assertThat(options.get().getWriteTimeout()).isEqualTo(writeTimeout);
    // unset read timeouts leave the azure-core defaults in place
    assertThat(options.get().getResponseTimeout()).isEqualTo(AZURE_DEFAULT_TIMEOUT);
    assertThat(options.get().getReadTimeout()).isEqualTo(AZURE_DEFAULT_TIMEOUT);
  }

  @Test
  void shouldKeepClientDefaultsWhenNoTimeoutIsSet() {
    // when
    final var options = AzureBackupStore.httpClientOptions(configWith(builder -> builder));

    // then
    assertThat(options).isEmpty();
  }

  private static AzureBackupConfig configWith(
      final UnaryOperator<AzureBackupConfig.Builder> timeouts) {
    return timeouts
        .apply(
            new AzureBackupConfig.Builder()
                .withEndpoint("https://localhost")
                .withContainerName("container"))
        .build();
  }
}
