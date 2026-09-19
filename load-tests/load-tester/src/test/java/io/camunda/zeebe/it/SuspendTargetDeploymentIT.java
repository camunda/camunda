/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.zeebe.LoadTesterApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test for {@code bpmn/suspend_target.bpmn}, the heavy target model used by the
 * suspend/resume blast-radius load test. Verifies against a real engine that the model deploys
 * (running the full deployment validation) and that an instance with a small fan-out can be created
 * — exercising the multi-instance branches and the {@code =timerDuration}/{@code =subItem}
 * expressions at runtime.
 */
@Testcontainers
@SpringBootTest(classes = LoadTesterApplication.class)
@ActiveProfiles("it")
class SuspendTargetDeploymentIT {

  @Container
  static final CamundaContainer CAMUNDA = CamundaContainerProvider.createCamundaContainer();

  @Autowired private CamundaClient client;

  @DynamicPropertySource
  static void configure(final DynamicPropertyRegistry registry) {
    CamundaContainerProvider.registerClientProperties(CAMUNDA, registry);
  }

  @Test
  void shouldDeployAndInstantiateSuspendTarget() {
    // given - the heavy target model on the classpath

    // when - it is deployed
    final var deployment =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("bpmn/suspend_target.bpmn")
            .send()
            .join();

    // then - deployment succeeds and exposes the suspendTarget process
    assertThat(deployment.getProcesses())
        .extracting(Process::getBpmnProcessId)
        .contains("suspendTarget");

    // when - an instance is created with a small fan-out (exercises the MI branches and the
    // =timerDuration / =subItem expressions)
    final var instance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("suspendTarget")
            .latestVersion()
            .variables(
                Map.of(
                    "jobs", List.of(1, 2),
                    "subs", List.of("a", "b"),
                    "timers", List.of(1, 2),
                    "timerDuration", "PT1H"))
            .send()
            .join();

    // then - the instance was created
    assertThat(instance.getProcessInstanceKey()).isPositive();
  }
}
