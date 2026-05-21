/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.properties.CamundaClientAuthProperties.AuthMethod;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.client.spring.properties.CamundaClientProperties.ClientMode;
import io.camunda.zeebe.LoadTesterApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Validates that {@code camunda.client.*} keys from {@code src/main/resources/application.yaml}
 * bind correctly onto the starter's {@link CamundaClientProperties}. Complements {@code ConfigTest}
 * which covers only the {@code load-tester.*} namespace.
 *
 * <p>Uses {@code @SpringBootTest} so the full environment post-processor pipeline runs (legacy-key
 * remapping, mode-based overlay application via {@code modes/self-managed.yaml}, auth-method
 * overlay via {@code auth-methods/none.yaml}). No Testcontainers are needed — no profile is active,
 * so neither {@code Starter} nor {@code Worker} is instantiated and no broker connection is
 * attempted.
 */
@SpringBootTest(classes = LoadTesterApplication.class)
class CamundaClientConfigIT {

  @Autowired private CamundaClientProperties clientProps;

  @Test
  void shouldBindClientPropertiesFromApplicationYaml() {
    // given / when - main application.yaml is loaded, no profile active, no env vars set

    // then - addresses resolve the ${ZEEBE_GRPC_ADDRESS:http://localhost:26500} placeholder
    assertThat(clientProps.getGrpcAddress()).hasToString("http://localhost:26500");
    assertThat(clientProps.getRestAddress()).hasToString("http://localhost:8080");

    // transport + mode
    assertThat(clientProps.getPreferRestOverGrpc()).isTrue();
    assertThat(clientProps.getMode()).isEqualTo(ClientMode.selfManaged);
    // client-side load balancing: randomized DNS + short connection TTL so pooled
    // connections rotate across gateway pod IPs. POJO default is false; yaml opts in.
    assertThat(clientProps.isUseClientSideLoadBalancing()).isTrue();

    // starter default: client has no worker threads (execution-threads: 0 in main yaml)
    assertThat(clientProps.getExecutionThreads()).isZero();

    // auth defaults to "none" (main yaml uses ${ZEEBE_AUTH_METHOD:none} placeholder)
    assertThat(clientProps.getAuth().getMethod()).isEqualTo(AuthMethod.none);

    // worker defaults (camunda.client.worker.defaults.*) — consumed by @JobWorker processing
    final var workerDefaults = clientProps.getWorker().getDefaults();
    assertThat(workerDefaults.getName()).isEqualTo("benchmark-worker");
    assertThat(workerDefaults.getType()).isEqualTo("benchmark-task");
    assertThat(workerDefaults.getStreamEnabled()).isTrue();
    assertThat(workerDefaults.getMaxJobsActive()).isEqualTo(30);
    assertThat(workerDefaults.getPollInterval()).hasSeconds(1);
    assertThat(workerDefaults.getTimeout()).hasMillis(1800);
  }
}
