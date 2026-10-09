/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AwsSecretsManagerStoreConfigTest {

  @Test
  void shouldRejectNegativeMaxRetries() {
    // when / then
    assertThatThrownBy(
            () -> new AwsSecretsManagerStoreConfig(null, null, null, null, -1, false, 20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxRetries");
  }

  @Test
  void shouldRejectBatchSizeBelowOne() {
    // when / then
    assertThatThrownBy(
            () ->
                new AwsSecretsManagerStoreConfig(
                    null,
                    null,
                    null,
                    null,
                    AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
                    true,
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
  }

  @Test
  void shouldRejectBatchSizeAboveMax() {
    // when / then
    assertThatThrownBy(
            () ->
                new AwsSecretsManagerStoreConfig(
                    null,
                    null,
                    null,
                    null,
                    AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
                    true,
                    21))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
  }

  @Test
  void shouldRejectBatchEnabledWithContainerSecretId() {
    // when / then
    assertThatThrownBy(
            () ->
                new AwsSecretsManagerStoreConfig(
                    null,
                    null,
                    "app-config",
                    null,
                    AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
                    true,
                    20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mutually exclusive");
  }

  @Test
  void shouldRejectBlankContainerSecretId() {
    // when / then
    assertThatThrownBy(
            () ->
                new AwsSecretsManagerStoreConfig(
                    null,
                    null,
                    "   ",
                    null,
                    AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
                    false,
                    20))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("containerSecretId");
  }

  @Test
  void shouldDefaultRetriesAndBatchingInFactory() {
    // when
    final var config = AwsSecretsManagerStoreConfig.of("camunda/");

    // then
    assertThat(config.maxRetries()).isEqualTo(AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES);
    assertThat(config.pathPrefix()).isEqualTo("camunda/");
    assertThat(config.region()).isNull();
    assertThat(config.containerSecretId()).isNull();
    assertThat(config.batchEnabled()).isFalse();
    assertThat(config.batchSize()).isEqualTo(AwsSecretsManagerStoreConfig.DEFAULT_BATCH_SIZE);
  }

  @Test
  void shouldDefaultTimeoutsInFactory() {
    // when
    final var config = AwsSecretsManagerStoreConfig.of("camunda/");

    // then
    assertThat(config.callTimeout()).isEqualTo(AwsSecretsManagerStoreConfig.DEFAULT_CALL_TIMEOUT);
    assertThat(config.attemptTimeout())
        .isEqualTo(AwsSecretsManagerStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);
  }

  @Test
  void shouldDefaultTimeoutsInBackwardsCompatibleConstructor() {
    // when
    final var config =
        new AwsSecretsManagerStoreConfig(
            null, null, null, null, AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES, false, 20);

    // then
    assertThat(config.callTimeout()).isEqualTo(AwsSecretsManagerStoreConfig.DEFAULT_CALL_TIMEOUT);
    assertThat(config.attemptTimeout())
        .isEqualTo(AwsSecretsManagerStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);
  }

  @Test
  void shouldRejectNonPositiveCallTimeout() {
    // when / then
    assertThatThrownBy(() -> configWithTimeouts(Duration.ZERO, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("callTimeout");
  }

  @Test
  void shouldRejectNonPositiveAttemptTimeout() {
    // when / then
    assertThatThrownBy(() -> configWithTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("attemptTimeout");
  }

  @Test
  void shouldRejectAttemptTimeoutLongerThanCallTimeout() {
    // given an attempt bound wider than the total bound, which can never take effect

    // when / then
    assertThatThrownBy(() -> configWithTimeouts(Duration.ofSeconds(2), Duration.ofSeconds(5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("attemptTimeout");
  }

  @Test
  void shouldRejectNullTimeouts() {
    // when / then
    assertThatThrownBy(() -> configWithTimeouts(null, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("callTimeout");
    assertThatThrownBy(() -> configWithTimeouts(Duration.ofSeconds(5), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("attemptTimeout");
  }

  private static AwsSecretsManagerStoreConfig configWithTimeouts(
      final Duration callTimeout, final Duration attemptTimeout) {
    return new AwsSecretsManagerStoreConfig(
        null,
        null,
        null,
        null,
        AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
        false,
        20,
        callTimeout,
        attemptTimeout);
  }
}
