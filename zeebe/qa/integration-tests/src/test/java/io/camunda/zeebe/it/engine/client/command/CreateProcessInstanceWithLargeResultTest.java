/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static io.camunda.client.impl.util.DataSizeUtil.ONE_MB;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.ByteValue;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
final class CreateProcessInstanceWithLargeResultTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(
              c -> {
                c.getCluster().getNetwork().setMaxMessageSize(DataSize.ofMegabytes(21));
                c.getData()
                    .getPrimaryStorage()
                    .getLogStream()
                    .setLogSegmentSize(DataSize.ofMegabytes(64));
              });

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void createClient() {
    client =
        ZEEBE
            .newClientBuilder()
            .defaultRequestTimeout(Duration.ofSeconds(10))
            .maxMessageSize(21 * ONE_MB)
            .build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldCreateInstanceWithLargeResult() {
    // given
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess("PROCESS").startEvent().endEvent().done());

    // when
    final ProcessInstanceResult processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("PROCESS")
            .latestVersion()
            .variables(Map.of("variable", "x".repeat((int) (ByteValue.ofMegabytes(10)))))
            .withResult()
            .send()
            .join();

    // then
    assertThat(processInstance.getVariables().getBytes(StandardCharsets.UTF_8))
        .hasSizeGreaterThan((int) ByteValue.ofMegabytes(10));
  }

  @Test
  void shouldCreateInstanceWithLargeResultAndSingleVariable() {
    // given
    resourcesHelper.deployProcess(
        Bpmn.createExecutableProcess("PROCESS").startEvent().endEvent().done());

    // when
    final ProcessInstanceResult processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("PROCESS")
            .latestVersion()
            .variable("variable", "x".repeat((int) (ByteValue.ofMegabytes(10))))
            .withResult()
            .send()
            .join();

    // then
    assertThat(processInstance.getVariables().getBytes(StandardCharsets.UTF_8))
        .hasSizeGreaterThan((int) ByteValue.ofMegabytes(10));
  }
}
