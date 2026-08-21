/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ZeebeSecretsDriverPropertiesTest {

  @Test
  void shouldComputeRatePerSecond() {
    // given
    final var properties = new ZeebeSecretsDriverProperties();
    properties.setRate(120);
    properties.setRateDuration(Duration.ofMinutes(1));

    // when / then
    assertThat(properties.getRatePerSecond()).isEqualTo(2.0);
  }

  @Test
  void shouldBuildReferencePoolWithConfiguredPrefixAndBaseName() {
    // given
    final var properties = new ZeebeSecretsDriverProperties();
    properties.setReferencePrefix("camunda.secrets.");
    properties.setReferenceBaseName("bench_");
    properties.setReferencePoolSize(3);

    // when
    final var pool = properties.buildReferencePool();

    // then
    assertThat(pool)
        .containsExactly(
            "camunda.secrets.bench_0", "camunda.secrets.bench_1", "camunda.secrets.bench_2");
  }

  @Test
  void shouldClampEffectiveBatchSizeToGatewayMaximum() {
    // given
    final var properties = new ZeebeSecretsDriverProperties();
    properties.setBatchSize(50);
    properties.setReferencePoolSize(100);

    // when / then
    assertThat(properties.getEffectiveBatchSize())
        .isEqualTo(ZeebeSecretsDriverProperties.MAX_BATCH_SIZE);
  }

  @Test
  void shouldClampEffectiveBatchSizeToReferencePoolSize() {
    // given
    final var properties = new ZeebeSecretsDriverProperties();
    properties.setBatchSize(10);
    properties.setReferencePoolSize(4);

    // when / then
    assertThat(properties.getEffectiveBatchSize()).isEqualTo(4);
  }

  @Test
  void shouldNeverProduceEmptyBatch() {
    // given
    final var properties = new ZeebeSecretsDriverProperties();
    properties.setBatchSize(0);
    properties.setReferencePoolSize(10);

    // when / then
    assertThat(properties.getEffectiveBatchSize()).isEqualTo(1);
  }
}
