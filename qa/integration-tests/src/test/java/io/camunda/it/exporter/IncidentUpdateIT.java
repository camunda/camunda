/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.utils.BrokerWithCamundaExporterITInvocationProvider;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.search.filter.IncidentFilter;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.Incident;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(BrokerWithCamundaExporterITInvocationProvider.class)
final class IncidentUpdateIT {

  private static final String CALL_ACTIVITY_ID = "child";
  private static final String TASK_ID = "task";
  private final String parentProcessId = Strings.newRandomValidBpmnId();
  private final String childProcessId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @TestTemplate
  void shouldMarkAllInstancesWithIncident(final ZeebeClient client) {
    // given
    final var parentInstanceKey = createUndeployedProcessInstance(client);
    final var childInstanceKey = getChildProcessInstanceKey(client);
    triggerIncidentOnJob(client);

    // when
    final var incidents =
        waitForIncident(client, f -> f.processInstanceKey(childInstanceKey).state("ACTIVE"));

    // then
    assertIncidentState(client, incidents.getIncidentKey(), "ACTIVE");
    assertProcessInstanceIncidentState(client, parentInstanceKey, true);
    assertProcessInstanceIncidentState(client, childInstanceKey, true);
    assertFlowNodeInstanceIncidentState(client, parentInstanceKey, CALL_ACTIVITY_ID, true);
    assertFlowNodeInstanceIncidentState(client, childInstanceKey, TASK_ID, true);
  }

  @TestTemplate
  void shouldUnmarkAllInstancesWithIncident(final ZeebeClient client) {
    // given
    final var parentInstanceKey = createUndeployedProcessInstance(client);
    final var childInstanceKey = getChildProcessInstanceKey(client);
    triggerIncidentOnJob(client);

    // when
    final var incident =
        waitForIncident(client, f -> f.processInstanceKey(childInstanceKey).state("ACTIVE"));
    client.newResolveIncidentCommand(incident.getIncidentKey()).send().join();

    // then
    assertIncidentState(client, incident.getIncidentKey(), "RESOLVED");
    assertProcessInstanceIncidentState(client, parentInstanceKey, false);
    assertProcessInstanceIncidentState(client, childInstanceKey, false);
    assertFlowNodeInstanceIncidentState(client, parentInstanceKey, CALL_ACTIVITY_ID, false);
    assertFlowNodeInstanceIncidentState(client, childInstanceKey, TASK_ID, false);
  }

  private void assertIncidentState(
      final ZeebeClient client, final long key, final String expected) {
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
      final ZeebeClient client, final long key, final boolean expected) {
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
      final ZeebeClient client,
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
      final ZeebeClient client, final long parentInstanceKey, final String callActivityId) {
    return client
        .newFlownodeInstanceQuery()
        .filter(f -> f.processInstanceKey(parentInstanceKey).flowNodeId(callActivityId))
        .send()
        .join()
        .items()
        .getFirst();
  }

  private ProcessInstance getProcessInstance(
      final ZeebeClient client, final long childInstanceKey) {
    return client
        .newProcessInstanceQuery()
        .filter(p -> p.processInstanceKey(childInstanceKey))
        .send()
        .join()
        .items()
        .getFirst();
  }

  private long getChildProcessInstanceKey(final ZeebeClient client) {
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

  private void triggerIncidentOnJob(final ZeebeClient client) {
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

  private long createUndeployedProcessInstance(final ZeebeClient client) {
    deployProcesses(client);
    return createProcessInstance(client);
  }

  private long createProcessInstance(final ZeebeClient client) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(parentProcessId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void deployProcesses(final ZeebeClient client) {
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

  private Incident waitForIncident(
      final ZeebeClient client, final Consumer<IncidentFilter> filterFn) {
    return Awaitility.await()
        .ignoreExceptions()
        .timeout(Duration.ofSeconds(30))
        .until(
            () -> client.newIncidentQuery().filter(filterFn).send().join().items(),
            Predicate.not(List::isEmpty))
        .getFirst();
  }
}
