/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.client.api.search.response.IncidentState.ACTIVE;
import static io.camunda.client.api.search.response.IncidentState.RESOLVED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.IncidentErrorType;
import io.camunda.client.api.search.response.IncidentState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class IncidentIT {

  private static CamundaClient client;
  private static final String CALL_ACTIVITY_ID = "child";
  private static final String TASK_ID = "task";
  private final String parentProcessId = Strings.newRandomValidBpmnId();
  private final String childProcessId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @Test
  void shouldExportIncident() {
    final var resource =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/error-end-event.bpmn")
            .send()
            .join();

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(resource.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    waitForIncident(client, f -> f.processInstanceKey(processInstanceKey));

    final var incidents =
        client
            .newIncidentQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items();

    // then
    assertThat(incidents).isNotEmpty();
    assertThat(incidents.size()).isEqualTo(1);
    assertThat(incidents.getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidents.getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldMarkAllInstancesWithIncident() {
    // given
    final var parentInstanceKey = createUndeployedProcessInstance(client);
    final var childInstanceKey = getChildProcessInstanceKey(client);
    triggerIncidentOnJob(client);

    // when
    final var incidents =
        waitForIncident(client, f -> f.processInstanceKey(childInstanceKey).state(ACTIVE));

    // then
    assertIncidentState(client, incidents.getIncidentKey(), ACTIVE);
    assertProcessInstanceIncidentState(client, parentInstanceKey, true);
    assertProcessInstanceIncidentState(client, childInstanceKey, true);
    assertFlowNodeInstanceIncidentState(client, parentInstanceKey, CALL_ACTIVITY_ID, true);
    assertFlowNodeInstanceIncidentState(client, childInstanceKey, TASK_ID, true);
  }

  @Test
  void shouldUnmarkAllInstancesWithIncident() {
    // given
    final var parentInstanceKey = createUndeployedProcessInstance(client);
    final var childInstanceKey = getChildProcessInstanceKey(client);
    triggerIncidentOnJob(client);

    // when
    final var incident =
        waitForIncident(client, f -> f.processInstanceKey(childInstanceKey).state(ACTIVE));
    client.newResolveIncidentCommand(incident.getIncidentKey()).send().join();

    // then
    assertIncidentState(client, incident.getIncidentKey(), RESOLVED);
    assertProcessInstanceIncidentState(client, parentInstanceKey, false);
    assertProcessInstanceIncidentState(client, childInstanceKey, false);
    assertFlowNodeInstanceIncidentState(client, parentInstanceKey, CALL_ACTIVITY_ID, false);
    assertFlowNodeInstanceIncidentState(client, childInstanceKey, TASK_ID, false);
  }

  private void assertIncidentState(
      final CamundaClient client, final long key, final IncidentState expected) {
    Awaitility.await("until incident %d state is = %s".formatted(key, expected))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var incident =
                  client.newIncidentQuery().filter(f -> f.incidentKey(key)).send().join().items();
              assertThat(incident).hasSize(1).first().returns(expected, Incident::getState);
            });
  }

  private void assertProcessInstanceIncidentState(
      final CamundaClient client, final long key, final boolean expected) {
    Awaitility.await("until process instance %d incident state = %s".formatted(key, expected))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var parentProcessInstance = getProcessInstance(client, key);
              assertThat(parentProcessInstance)
                  .as("has incident = %s".formatted(expected))
                  .returns(expected, ProcessInstance::getHasIncident);
            });
  }

  private void assertFlowNodeInstanceIncidentState(
      final CamundaClient client,
      final long processInstanceKey,
      final String flowNodeId,
      final boolean expected) {
    Awaitility.await(
            "until flow node %s in process instance %d incident state = %s"
                .formatted(flowNodeId, processInstanceKey, expected))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var taskFlowNode = getFlowNodeInstance(client, processInstanceKey, flowNodeId);
              assertThat(taskFlowNode)
                  .as("has incident = %s".formatted(expected))
                  .returns(expected, FlowNodeInstance::getIncident);
            });
  }

  private FlowNodeInstance getFlowNodeInstance(
      final CamundaClient client, final long parentInstanceKey, final String callActivityId) {
    return client
        .newFlownodeInstanceQuery()
        .filter(f -> f.processInstanceKey(parentInstanceKey).flowNodeId(callActivityId))
        .send()
        .join()
        .items()
        .getFirst();
  }

  private ProcessInstance getProcessInstance(
      final CamundaClient client, final long childInstanceKey) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(p -> p.processInstanceKey(childInstanceKey))
        .send()
        .join()
        .items()
        .getFirst();
  }

  private long getChildProcessInstanceKey(final CamundaClient client) {
    return Awaitility.await("until one flow node exists in the child process instance")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newFlownodeInstanceQuery()
                    .filter(f -> f.processDefinitionId(childProcessId))
                    .send()
                    .join()
                    .items(),
            Predicate.not(List::isEmpty))
        .getFirst()
        .getProcessInstanceKey();
  }

  private void triggerIncidentOnJob(final CamundaClient client) {
    final var job =
        client
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(1)
            .send()
            .join()
            .getJobs()
            .getFirst();
    client.newThrowErrorCommand(job).errorCode("unknown").send().join();
  }

  private long createUndeployedProcessInstance(final CamundaClient client) {
    deployProcesses(client);
    return createProcessInstance(client);
  }

  private long createProcessInstance(final CamundaClient client) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(parentProcessId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void deployProcesses(final CamundaClient client) {
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(CALL_ACTIVITY_ID, b -> b.zeebeProcessId(childProcessId))
            .endEvent()
            .done();
    final var childProcess =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .serviceTask(TASK_ID, b -> b.zeebeJobType(jobType))
            .endEvent()
            .done();
    client
        .newDeployResourceCommand()
        .addProcessModel(parentProcess, "parent.bpmn")
        .addProcessModel(childProcess, "child.bpmn")
        .send()
        .join();
  }

  @Test
  void shouldExportUnhandledErrorIncident() {

    final var resource =
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath("process/errorProcess.bpmn")
            .send()
            .join();

    final var processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(resource.getProcesses().getFirst().getBpmnProcessId())
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();

    throwIncident(client, "errorTask", "this-errorcode-does-not-exists", "Process error");

    waitForIncident(client, f -> f.processInstanceKey(processInstanceKey));

    final var incidents =
        client
            .newIncidentQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join()
            .items();

    // then
    assertThat(incidents).isNotEmpty();
    assertThat(incidents.size()).isEqualTo(1);
    assertThat(incidents.getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.UNHANDLED_ERROR_EVENT);
    assertThat(incidents.getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  private Incident waitForIncident(
      final CamundaClient client, final Consumer<IncidentFilter> filterFn) {
    return Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () -> client.newIncidentQuery().filter(filterFn).send().join().items(),
            Predicate.not(List::isEmpty))
        .getFirst();
  }

  private void throwIncident(
      final CamundaClient client,
      final String jobType,
      final String errorCode,
      final String errorMessage) {
    client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .workerName(UUID.randomUUID().toString())
        .send()
        .join()
        .getJobs()
        .forEach(
            j -> {
              final var inc =
                  client
                      .newThrowErrorCommand(j.getKey())
                      .errorCode(errorCode)
                      .errorMessage(errorMessage)
                      .send()
                      .join();
            });
  }
}
