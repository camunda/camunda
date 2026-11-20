/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class EmbeddedGatewayWithOneCpuThreadIT {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker().withUnifiedConfig(cfg -> cfg.getSystem().setCpuThreadCount(1));

  @AutoClose private CamundaClient client;

  @Test
  void shouldStartEmbeddedGateway() {
    // given
    client = broker.newClientBuilder().build();

    // when
    final var topology = client.newTopologyRequest().send().join();

    // then
    assertThat(topology.getBrokers()).hasSize(1);
  }

  @Test
  void shouldDeployProcess() {
    // given
    client = broker.newClientBuilder().build();
    final var process = Bpmn.createExecutableProcess().startEvent().endEvent().done();

    // when
    final var result = client.newDeployCommand().addProcessModel(process, "foo.bpmn").send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcesses()).hasSize(1);
  }
}
