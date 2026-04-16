/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.spring.utils.DatabaseTypeUtils.CAMUNDA_DATABASE_TYPE_NONE;
import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class DeployResourceWithoutSecondaryStorageIT {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE, CAMUNDA_DATABASE_TYPE_NONE);

  @AutoClose private CamundaClient camundaClient;

  @BeforeEach
  void setUp() {
    camundaClient = broker.newClientBuilder().preferRestOverGrpc(true).build();
  }

  @Test
  void shouldDeployRpaResourceAndFetchMetadataWithoutSecondaryStorage() {
    // given
    final var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .execute();
    final long resourceKey = deployment.getResource().getFirst().getResourceKey();

    // when - broker fetch path (no secondary storage)
    final var resource = camundaClient.newResourceGetRequest(resourceKey).execute();

    // then
    assertThat(resource).isNotNull();
    assertThat(resource.getResourceKey()).isEqualTo(resourceKey);
    assertThat(resource.getResourceId()).isEqualTo("RPA_auditlog_test");
    assertThat(resource.getResourceName()).isEqualTo("rpa/test-rpa.rpa");
    assertThat(resource.getVersion()).isEqualTo(1);

    // and content should also be retrievable directly from the broker
    final var content = camundaClient.newResourceContentGetRequest(resourceKey).execute();
    assertThat(content).isNotNull().isNotEmpty();
  }
}
