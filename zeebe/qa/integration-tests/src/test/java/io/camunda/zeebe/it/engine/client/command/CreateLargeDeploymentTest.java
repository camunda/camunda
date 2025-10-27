/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.util.ByteValue;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
@AutoCloseResources
public final class CreateLargeDeploymentTest {

  private static final int MAX_MSG_SIZE_MB = 1;

  ZeebeClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(
              b -> b.getNetwork().setMaxMessageSize(DataSize.ofMegabytes(MAX_MSG_SIZE_MB)));

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  // Regression "https://github.com/camunda/camunda/issues/12591")
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectDeployIfResourceIsTooLarge(final boolean useRest) {
    // when
    final var deployLargeProcess =
        getCommand(client, useRest)
            .addProcessModel(
                Bpmn.createExecutableProcess("PROCESS")
                    .startEvent()
                    .documentation("x".repeat((int) ByteValue.ofMegabytes(MAX_MSG_SIZE_MB)))
                    .done(),
                "too_large_process.bpmn")
            .send();

    // then
    assertThatThrownBy(deployLargeProcess::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Request size is above configured maxMessageSize.");

    // then - can deploy another process
    final var deployedValidProcess =
        getCommand(client, useRest)
            .addResourceFromClasspath("processes/one-task-process.bpmn")
            .send()
            .join();
    assertThat(deployedValidProcess.getProcesses()).hasSize(1);
  }

  private DeployResourceCommandStep1 getCommand(final ZeebeClient client, final boolean useRest) {
    final DeployResourceCommandStep1 deployResourceCommand = client.newDeployResourceCommand();
    return useRest ? deployResourceCommand.useRest() : deployResourceCommand.useGrpc();
  }
}
