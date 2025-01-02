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
import io.camunda.client.api.command.ProblemException;
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
class CreateTenantTest {

  @AutoCloseResource CamundaClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldCreateTenant() {
    // when
    final var response =
        client.newCreateTenantCommand().tenantId("tenant-id").name("Tenant Name").send().join();

    // then
    assertThat(response.getTenantKey()).isGreaterThan(0);
    ZeebeAssertHelper.assertTenantCreated(
        "tenant-id",
        (tenant) -> {
          assertThat(tenant.getName()).isEqualTo("Tenant Name");
        });
  }

  @Test
  void shouldRejectIfTenantIdAlreadyExists() {
    // given
    client.newCreateTenantCommand().tenantId("tenant-id").name("Tenant Name").send().join();

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateTenantCommand()
                    .tenantId("tenant-id")
                    .name("Another Tenant Name")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'")
        .hasMessageContaining("a tenant with this ID already exists");
  }

  @Test
  void shouldRejectIfMissingTenantId() {
    // when / then
    assertThatThrownBy(() -> client.newCreateTenantCommand().name("Tenant Name").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }
}