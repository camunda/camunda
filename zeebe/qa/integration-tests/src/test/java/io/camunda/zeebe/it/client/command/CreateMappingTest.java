/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
public class CreateMappingTest {

  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateMapping() {
    // when
    final var response =
        client
            .newCreateMappingCommand()
            .claimName(CLAIM_NAME)
            .claimValue(CLAIM_VALUE)
            .send()
            .join();

    // then
    assertThat(response.getMappingKey()).isGreaterThan(0);
    ZeebeAssertHelper.assertMappingCreated(CLAIM_NAME, CLAIM_VALUE);
  }

  @Test
  void shouldRejectIfMissingClaimName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateMappingCommand().claimValue(CLAIM_VALUE).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimName");
  }

  @Test
  void shouldRejectIfMissingClaimValue() {
    // when / then
    assertThatThrownBy(() -> client.newCreateMappingCommand().claimName(CLAIM_NAME).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimValue");
  }

  @Test
  void shouldRejectIfMappingAlreadyExists() {
    // given
    client.newCreateMappingCommand().claimName(CLAIM_NAME).claimValue(CLAIM_VALUE).send().join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingCommand()
                    .claimName(CLAIM_NAME)
                    .claimValue(CLAIM_VALUE)
                    .send()
                    .join())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to create mapping with claimName 'claimName' and claimValue 'claimValue', but a mapping with this claim already exists.");
  }
}
