/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.LoadTesterApplication;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Validates that {@code camunda.client.*} keys from {@code src/main/resources/application.yaml}
 * bind correctly onto the starter's {@link CamundaClientProperties}. Complements {@code ConfigTest}
 * which covers only the {@code load-tester.*} namespace.
 */
@SpringBootTest(classes = LoadTesterApplication.class)
class CamundaClientConfigIT {

  @Autowired private CamundaClientProperties clientProps;

  @Test
  void shouldBindClientPropertiesFromApplicationYaml() {
    // given / when - main application.yaml is loaded, no profile active, no env vars set

    // then - addresses and transport on the nested zeebe section
    assertThat(clientProps.getZeebe().getGrpcAddress()).hasToString("http://localhost:26500");
    assertThat(clientProps.getZeebe().getRestAddress()).hasToString("http://localhost:8080");
    assertThat(clientProps.getZeebe().getPreferRestOverGrpc()).isTrue();

    // mode
    assertThat(clientProps.getMode()).isEqualTo(ClientMode.selfManaged);

    // starter default: client has no worker threads (execution-threads: 0 in main yaml)
    assertThat(clientProps.getZeebe().getExecutionThreads()).isZero();

    // worker defaults (camunda.client.zeebe.defaults.*) — consumed by @JobWorker processing
    final var workerDefaults = clientProps.getZeebe().getDefaults();
    assertThat(workerDefaults.getName()).isEqualTo("benchmark-worker");
    assertThat(workerDefaults.getType()).isEqualTo("benchmark-task");
    assertThat(workerDefaults.getStreamEnabled()).isTrue();
    assertThat(workerDefaults.getMaxJobsActive()).isEqualTo(30);
    assertThat(workerDefaults.getPollInterval()).hasSeconds(1);
    assertThat(workerDefaults.getTimeout()).hasMillis(1800);
  }
}
