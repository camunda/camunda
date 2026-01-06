/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.Future;

@ZeebeIntegration
public class RejectLargeDeploymentTest {
  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @RegressionTest("https://github.com/camunda/camunda/issues/15989")
  void shouldExportLargeDeploymentRejection() {
    // given - a deployment with a large, unparsable input expression
    final var data = "x".repeat(Short.MAX_VALUE * 2);
    final var process =
        Bpmn.createExecutableProcess("test")
            .startEvent()
            .serviceTask(
                "task", task -> task.zeebeInput("=<DOCTYPE!" + data, "var").zeebeJobType("test"))
            .endEvent()
            .done();

    // when -- deploying the process
    try (final var client = ZEEBE.newClientBuilder().build()) {
      final var response =
          client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send();
      assertThat((Future<DeploymentEvent>) response).failsWithin(Duration.ofSeconds(5));
    }

    // then -- The rejection can be exported
    assertThat(
            RecordingExporter.deploymentRecords()
                .withRecordType(RecordType.COMMAND_REJECTION)
                .exists())
        .isTrue();
  }
}
