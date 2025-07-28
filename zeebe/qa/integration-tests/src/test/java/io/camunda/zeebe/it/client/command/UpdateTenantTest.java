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
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class UpdateTenantTest {

  private static final String UPDATED_TENANT_NAME = "Updated Tenant Name";
  private static final String UPDATED_TENANT_DESCRIPTION = "Updated Tenant Description";
  private static final String TENANT_ID = "tenantId";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    client.newCreateTenantCommand().tenantId(TENANT_ID).name("Initial Tenant Name").send().join();
  }

  @Test
  void shouldUpdateTenantNameAndDescription() {
    // when
    client
        .newUpdateTenantCommand(TENANT_ID)
        .name(UPDATED_TENANT_NAME)
        .description(UPDATED_TENANT_DESCRIPTION)
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertTenantUpdated(
        TENANT_ID, tenant -> assertThat(tenant.getName()).isEqualTo(UPDATED_TENANT_NAME));
  }

  @Test
  void shouldRejectUpdateIfNameIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateTenantCommand(TENANT_ID)
                    .description("new description")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("No name provided");
  }

  @Test
  void shouldRejectUpdateIfDescriptionIsNull() {
    // when / then
    assertThatThrownBy(
            () -> client.newUpdateTenantCommand(TENANT_ID).name("new name").send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("No description provided");
  }

  @Test
  void shouldRejectUpdateIfTenantDoesNotExist() {
    // when / then
    final String notExistingTenantId = "does-not-exist";
    assertThatThrownBy(
            () ->
                client
                    .newUpdateTenantCommand(notExistingTenantId)
                    .name("Non-Existent Tenant Name")
                    .description("Some Description")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to update tenant with id '%s', but no tenant with this id exists."
                .formatted(notExistingTenantId));
  }
}
