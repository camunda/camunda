/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstances;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.SuspendProcessDefinitionVersionResult;
import io.camunda.client.api.response.SuspendProcessDefinitionVersionResult.Outcome;
import io.camunda.client.api.search.enums.ProcessDefinitionState;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Proves that suspend, resume and delete of a process definition, sent through the REST endpoints,
 * are exported to secondary storage and visible through the search API and get by key. The two
 * {@code DRAINING} cases are skipped on the relational database.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "(?i)aws_os")
class ProcessDefinitionSuspendedStateSearchIT {

  @MultiDbTestApplication static final TestStandaloneBroker BROKER = new TestStandaloneBroker();

  private static CamundaClient camundaClient;

  @Test
  void shouldReturnSuspendedVersionWhenSearchingForSuspendedState() {
    // given
    final var versions = deployTwoVersions();
    camundaClient
        .newSuspendProcessDefinitionCommand()
        .processDefinitionKey(versions.v2().getProcessDefinitionKey())
        .send()
        .join();
    awaitState(versions.v2(), ProcessDefinitionState.SUSPENDED);

    // when
    final var suspended =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(versions.v2().getBpmnProcessId())
                        .state(ProcessDefinitionState.SUSPENDED))
            .send()
            .join()
            .items();

    // then
    assertThat(suspended)
        .extracting(ProcessDefinition::getProcessDefinitionKey)
        .containsExactly(versions.v2().getProcessDefinitionKey());
    assertThat(suspended)
        .extracting(ProcessDefinition::getState)
        .containsOnly(ProcessDefinitionState.SUSPENDED);
  }

  @Test
  void shouldReturnLatestActiveVersionSkippingSuspendedVersion() {
    // given
    final var versions = deployTwoVersions();
    camundaClient
        .newSuspendProcessDefinitionCommand()
        .processDefinitionKey(versions.v2().getProcessDefinitionKey())
        .send()
        .join();
    awaitState(versions.v2(), ProcessDefinitionState.SUSPENDED);

    // when
    final var latestActive =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(versions.v2().getBpmnProcessId())
                        .isLatestVersion(true)
                        .state(ProcessDefinitionState.ACTIVE))
            .send()
            .join()
            .items();

    // then
    assertThat(latestActive)
        .extracting(ProcessDefinition::getProcessDefinitionKey)
        .containsExactly(versions.v1().getProcessDefinitionKey());
  }

  @Test
  void shouldReturnVersionAsActiveAfterResume() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var key = process.getProcessDefinitionKey();
    camundaClient.newSuspendProcessDefinitionCommand().processDefinitionKey(key).send().join();
    awaitState(process, ProcessDefinitionState.SUSPENDED);

    // when
    camundaClient.newResumeProcessDefinitionCommand().processDefinitionKey(key).send().join();

    // then
    awaitState(process, ProcessDefinitionState.ACTIVE);
    assertThat(searchByKeyAndState(key, ProcessDefinitionState.ACTIVE))
        .extracting(ProcessDefinition::getProcessDefinitionKey)
        .containsExactly(key);
    assertThat(searchByKeyAndState(key, ProcessDefinitionState.SUSPENDED)).isEmpty();
  }

  @Test
  @DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldMarkSuspendedVersionAsDrainingWhenDeleted() {
    // given - a user task keeps the instance running, so the deleted version drains
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().userTask("wait").endEvent().done(),
            processId + ".bpmn");
    final var key = process.getProcessDefinitionKey();
    startProcessInstance(camundaClient, processId);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionId(processId).state(ProcessInstanceState.ACTIVE), 1);
    camundaClient.newSuspendProcessDefinitionCommand().processDefinitionKey(key).send().join();
    awaitState(process, ProcessDefinitionState.SUSPENDED);

    // when
    camundaClient.newDeleteResourceCommand(key).deleteHistory(false).send().join();

    // then
    awaitState(process, ProcessDefinitionState.DRAINING);
    assertThat(searchByKeyAndState(key, ProcessDefinitionState.SUSPENDED)).isEmpty();
  }

  @Test
  @DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldKeepDrainingVersionWhenAllVersionsAreSuspended() {
    // given - v1 drains because its user task instance is still running
    final var processId = Strings.newRandomValidBpmnId();
    final var v1 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("wait1")
                .endEvent()
                .done(),
            processId + ".bpmn");
    startProcessInstance(camundaClient, processId);
    waitForProcessInstances(
        camundaClient, f -> f.processDefinitionId(processId).state(ProcessInstanceState.ACTIVE), 1);
    final var v2 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("wait2")
                .endEvent()
                .done(),
            processId + ".bpmn");
    camundaClient
        .newDeleteResourceCommand(v1.getProcessDefinitionKey())
        .deleteHistory(false)
        .send()
        .join();
    awaitState(v1, ProcessDefinitionState.DRAINING);

    // when - suspend all versions of the process ID
    final var response =
        camundaClient
            .newSuspendProcessDefinitionCommand()
            .processDefinitionId(processId)
            .send()
            .join();

    // then - the draining version keeps its outcome, the other version is suspended
    assertThat(response.getProcessDefinitions())
        .extracting(
            SuspendProcessDefinitionVersionResult::getProcessDefinitionKey,
            SuspendProcessDefinitionVersionResult::getOutcome)
        .containsExactlyInAnyOrder(
            tuple(v1.getProcessDefinitionKey(), Outcome.DRAINING),
            tuple(v2.getProcessDefinitionKey(), Outcome.SUSPENDED));
    awaitState(v2, ProcessDefinitionState.SUSPENDED);
    awaitState(v1, ProcessDefinitionState.DRAINING);
  }

  private static Versions deployTwoVersions() {
    final var processId = Strings.newRandomValidBpmnId();
    final var v1 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent("end1").done(),
            processId + ".bpmn");
    final var v2 =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent("end2").done(),
            processId + ".bpmn");
    assertThat(v2.getVersion()).isGreaterThan(v1.getVersion());
    return new Versions(v1, v2);
  }

  private static List<ProcessDefinition> searchByKeyAndState(
      final long key, final ProcessDefinitionState state) {
    return camundaClient
        .newProcessDefinitionSearchRequest()
        .filter(f -> f.processDefinitionKey(key).state(state))
        .send()
        .join()
        .items();
  }

  private static void awaitState(final Process process, final ProcessDefinitionState expected) {
    Awaitility.await("process definition should be " + expected)
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        camundaClient
                            .newProcessDefinitionGetRequest(process.getProcessDefinitionKey())
                            .send()
                            .join()
                            .getState())
                    .isEqualTo(expected));
  }

  private record Versions(Process v1, Process v2) {}
}
