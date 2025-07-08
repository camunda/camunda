/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Acceptance test to verify that Camunda can run in headless mode with no secondary storage.
 * This test validates the complete engine-only deployment scenario where database.type=none.
 */
@ZeebeIntegration
public class NoSecondaryStorageTest {

  @TestZeebe
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withProperty("camunda.database.type", "none")
          .withProperty("spring.profiles.active", "broker");

  @AutoClose private CamundaClient camundaClient;

  @BeforeEach
  void beforeEach() {
    camundaClient = broker.newClientBuilder().build();
  }

  @Test
  public void shouldRunHeadlessDeploymentWithBasicOperations() {
    // given - a simple process
    final var processId = Strings.newRandomValidBpmnId();
    final var process = createSimpleProcess(processId);

    // when - deploying and executing the process
    final var deploymentEvent = 
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(process, processId + ".bpmn")
            .send()
            .join();

    final var processInstanceEvent =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .send()
            .join();

    // then - verify deployment and execution succeed
    assertThat(deploymentEvent.getKey()).isPositive();
    assertThat(deploymentEvent.getProcesses()).hasSize(1);
    assertThat(deploymentEvent.getProcesses().get(0).getBpmnProcessId()).isEqualTo(processId);

    assertThat(processInstanceEvent.getBpmnProcessId()).isEqualTo(processId);
    assertThat(processInstanceEvent.getProcessInstanceKey()).isPositive();
  }

  @Test
  public void shouldHandleServiceTasksInHeadlessMode() {
    // given - a process with a service task
    final var processId = Strings.newRandomValidBpmnId();
    final var jobType = "test-service";
    final var process = createProcessWithServiceTask(processId, jobType);

    // when - deploying the process and creating an instance
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .send()
        .join();

    final var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    // then - verify job activation and completion work
    final var activateJobsResponse =
        camundaClient
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join();

    assertThat(activateJobsResponse.getJobs()).hasSize(1);
    final var job = activateJobsResponse.getJobs().get(0);
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstance.getProcessInstanceKey());

    // complete the job
    final var completeJobResponse =
        camundaClient
            .newCompleteCommand(job.getKey())
            .send()
            .join();

    assertThat(completeJobResponse).isNotNull();
  }

  @Test
  public void shouldHandleMultipleProcessInstances() {
    // given - a process
    final var processId = Strings.newRandomValidBpmnId();
    final var process = createSimpleProcess(processId);

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(process, processId + ".bpmn")
        .send()
        .join();

    // when - creating multiple process instances
    final var instance1 =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .send()
            .join();

    final var instance2 =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .withResult()
            .send()
            .join();

    // then - verify both instances are created successfully
    assertThat(instance1.getProcessInstanceKey()).isPositive();
    assertThat(instance2.getProcessInstanceKey()).isPositive();
    assertThat(instance1.getProcessInstanceKey()).isNotEqualTo(instance2.getProcessInstanceKey());
    assertThat(instance1.getBpmnProcessId()).isEqualTo(processId);
    assertThat(instance2.getBpmnProcessId()).isEqualTo(processId);
  }

  private BpmnModelInstance createSimpleProcess(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .endEvent("end")
        .done();
  }

  private BpmnModelInstance createProcessWithServiceTask(final String processId, final String jobType) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask("service")
        .zeebeJobType(jobType)
        .endEvent("end")
        .done();
  }
}