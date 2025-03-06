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

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class CreateMappingTest {

  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";
  public static final String NAME = "Map Name";
  public static final String ID = "mappingId";

  @AutoClose CamundaClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

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
            .name(NAME)
            .id(ID)
            .send()
            .join();

    // then
    assertThat(response.getMappingId()).isGreaterThan(0);
    ZeebeAssertHelper.assertMappingCreated(
        CLAIM_NAME,
        CLAIM_VALUE,
        mappingRecordValue -> assertThat(mappingRecordValue.getName()).isEqualTo(NAME));
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
    assertThatThrownBy(
            () -> client.newCreateMappingCommand().claimName(CLAIM_NAME).name(NAME).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimValue");
  }

  @Test
  void shouldRejectIfMissingId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingCommand()
                    .claimName(CLAIM_NAME)
                    .claimValue(CLAIM_VALUE)
                    .name(NAME)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  @Test
  void shouldRejectIfMissingName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingCommand()
                    .claimName(CLAIM_NAME)
                    .claimValue(CLAIM_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void shouldRejectIfMappingAlreadyExists() {
    // given
    client
        .newCreateMappingCommand()
        .claimName(CLAIM_NAME)
        .claimValue(CLAIM_VALUE)
        .name(NAME)
        .id(Strings.newRandomValidIdentityId())
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingCommand()
                    .claimName(CLAIM_NAME)
                    .claimValue(CLAIM_VALUE)
                    .name(NAME)
                    .id(Strings.newRandomValidIdentityId())
                    .send()
                    .join())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to create mapping with claimName 'claimName' and claimValue 'claimValue', but a mapping with this claim already exists.");
  }

  @Test
  void shouldRejectIfMappingSameIDAlreadyExists() {
    // given
    client
        .newCreateMappingCommand()
        .claimName("c1")
        .claimValue(CLAIM_VALUE)
        .name(NAME)
        .id(ID)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingCommand()
                    .claimName("c2")
                    .claimValue(CLAIM_VALUE)
                    .name(NAME)
                    .id(ID)
                    .send()
                    .join())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining(
            "Expected to create mapping with id '%s', but a mapping with this id already exists."
                .formatted(ID));
  }
}
