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
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class DeleteTenantTest {

  private static final String TENANT_ID = "tenant-id";

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    client.newCreateTenantCommand().tenantId(TENANT_ID).name("Tenant Name").send().join();
  }

  @Test
  void shouldDeleteTenant() {
    // when
    client.newDeleteTenantCommand(TENANT_ID).send().join();

    // then

    assertThat(
            RecordingExporter.tenantRecords()
                .withIntent(TenantIntent.DELETED)
                .withTenantId("tenant-id")
                .exists())
        .isTrue();
  }

  @Test
  void shouldRejectIfTenantDoesNotExist() {
    // given
    final var nonExistentTenantId = "does-not-exist";

    // when / then
    assertThatThrownBy(() -> client.newDeleteTenantCommand(nonExistentTenantId).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'")
        .hasMessageContaining(
            "Expected to delete tenant with id '%s', but no tenant with this id exists."
                .formatted(nonExistentTenantId));
  }
}
