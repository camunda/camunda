/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.Test;

@MultiDbTest
class DeployResourceIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldDeployRpaResourceAndFetchMetadataWithSecondaryStorage() {
    // given
    final var deployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("rpa/test-rpa.rpa")
            .execute();
    final long resourceKey = deployment.getResource().getFirst().getResourceKey();

    // when / then - wait for the exporter to sync the record to secondary storage
    await("resource metadata should be available in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var resource = camundaClient.newResourceGetRequest(resourceKey).execute();
              assertThat(resource).isNotNull();
              assertThat(resource.getResourceKey()).isEqualTo(resourceKey);
              assertThat(resource.getResourceId()).isEqualTo("RPA_auditlog_test");
              assertThat(resource.getResourceName()).isEqualTo("rpa/test-rpa.rpa");
              assertThat(resource.getVersion()).isEqualTo(1);
            });

    await("resource content should be available in secondary storage")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var content = camundaClient.newResourceContentGetRequest(resourceKey).execute();
              assertThat(content).isNotNull().isNotEmpty();
            });
  }
}
