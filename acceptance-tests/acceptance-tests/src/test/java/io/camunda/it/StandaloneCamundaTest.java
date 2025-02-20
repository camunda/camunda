/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestRestOperateClient.ProcessInstanceResult;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class StandaloneCamundaTest {

  @TestZeebe
  final TestStandaloneCamunda testStandaloneCamunda =
      new TestStandaloneCamunda().withUnauthenticatedAccess();

  @Test
  public void shouldCreateAndRetrieveInstance() {
    // given
    final var camundaClient = testStandaloneCamunda.newClientBuilder().build();

    // when
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("test")
                .zeebeJobType("type")
                .endEvent()
                .done(),
            "simple.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    final var operateClient = testStandaloneCamunda.newOperateClient();
    Awaitility.await("should receive data from ES")
        .timeout(Duration.ofMinutes(1))
        .untilAsserted(
            () -> {
              final Either<Exception, ProcessInstanceResult> eitherProcessInstanceResult =
                  operateClient.getProcessInstanceWith(
                      processInstanceEvent.getProcessInstanceKey());

              // has no exception
              assertThat(eitherProcessInstanceResult.isRight())
                  .withFailMessage("Expect no error on retrieving process instance")
                  .isTrue();

              final var processInstanceResult = eitherProcessInstanceResult.get();
              assertThat(processInstanceResult.total())
                  .withFailMessage("Expect to read a process instance from ES")
                  .isGreaterThan(0);

              assertThat(processInstanceResult.processInstances().getFirst().getKey())
                  .withFailMessage("Expect to read the expected process instance from ES")
                  .isEqualTo(processInstanceEvent.getProcessInstanceKey());
            });
  }
}
