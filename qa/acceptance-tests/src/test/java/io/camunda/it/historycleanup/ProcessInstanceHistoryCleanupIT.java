/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historycleanup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension.DatabaseType;
import io.camunda.qa.util.multidb.HistoryMultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@HistoryMultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ProcessInstanceHistoryCleanupIT {

  // To generate audit logs we need to have authenticated access
  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthenticatedAccess();

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceHistoryCleanupIT.class);
  @Authenticated private static CamundaClient client;
  private static DatabaseType databaseType;
  private static final String ROOT_PROCESS_ID = "rootProcess";
  private static final String CHILD_PROCESS_ID = "childProcess";
  private static final String DECISION_ID = "jedi_or_sith";
  private static final String MESSAGE_NAME = "message";
  private static final String JOB_TYPE = "job";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static Duration cleanupTimeout;
  private final AtomicBoolean shouldWorkerCompleteJob = new AtomicBoolean(false);

  @BeforeAll
  static void setup() {
    cleanupTimeout =
        switch (databaseType) {
          // OS cleanup can not be faster than 3min, and can take more time.
          case OS, AWS_OS -> Duration.ofMinutes(5);
          default -> Duration.ofSeconds(30);
        };
  }

  @Test
  void shouldDeleteInstancesDataOnlyWhenRootInstancesAreCompleted() {
    // Given
    deployResources(client);

    final long rootPiKey;
    final long childPiKey;

    try (final var ignored = openJobWorker(client)) {
      rootPiKey = startRootProcessInstance(client);
      childPiKey = getChildProcessInstanceKey(client, rootPiKey);

      handleIncident(client, childPiKey);
      completeUserTask(client, childPiKey);
      publishMessage(client, childPiKey);

      // Verify that child process instance is completed, but root is still active
      assertProcessInstanceState(client, childPiKey, ProcessInstanceState.COMPLETED);
      assertProcessInstanceState(client, rootPiKey, ProcessInstanceState.ACTIVE);

      // Verify data presence for child process instances (should exist because root is active)
      assertProcessInstanceDataIsPresent(client, childPiKey);

      // When: Complete root user task to fully complete the root process instance
      completeUserTask(client, rootPiKey);

      // Then: Root is completed
      assertProcessInstanceState(client, rootPiKey, ProcessInstanceState.COMPLETED);

      // And: Data should be cleaned up for both
      assertProcessInstanceDataIsCleanedUp(client, rootPiKey);
      assertProcessInstanceDataIsCleanedUp(client, childPiKey);
    }
  }

  private void assertProcessInstanceDataIsPresent(
      final CamundaClient client, final long processInstanceKey) {
    if (databaseType == DatabaseType.OS || databaseType == DatabaseType.AWS_OS) {
      // In OpenSearch, data cleanup only happens in the 3rd minute after completion due to ISM
      // policies, even when the ISM job interval is reduced to 1 minute for tests.
      // Therefore, we skip this check to avoid false negatives and longer wait times.
      return;
    }
    Awaitility.await("Wait and verify process instance data is still present")
        .logging(LOGGER::trace)
        .pollDelay(cleanupTimeout)
        .timeout(cleanupTimeout.plusSeconds(1))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              final var verifiers = getDataVerifiers(client, processInstanceKey);
              assertThat(verifiers)
                  .as("All verifiers should find data")
                  .allSatisfy(
                      (name, supplier) ->
                          assertThat(supplier.get()).as("Data for %s", name).isNotEmpty());
            });
  }

  private void assertProcessInstanceDataIsCleanedUp(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("Wait for process instance data cleanup")
        .logging(LOGGER::trace)
        .timeout(cleanupTimeout)
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var verifiers = getDataVerifiers(client, processInstanceKey);
              assertThat(verifiers)
                  .as("All verifiers should find no data")
                  .allSatisfy(
                      (name, supplier) ->
                          assertThat(supplier.get()).as("Data for %s", name).isEmpty());
            });
  }

  private Map<String, Supplier<Collection<?>>> getDataVerifiers(
      final CamundaClient client, final long processInstanceKey) {
    return Map.of(
        "process instance",
        () ->
            client
                .newProcessInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "user task",
        () ->
            client
                .newUserTaskSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "element instance",
        () ->
            client
                .newElementInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "variable",
        () ->
            client
                .newVariableSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "correlated message subscription",
        () ->
            client
                .newCorrelatedMessageSubscriptionSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "incident",
        () ->
            client
                .newIncidentSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "decision instance",
        () ->
            client
                .newDecisionInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "job",
        () ->
            client
                .newJobSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .send()
                .join()
                .items(),
        "sequence flow",
        () -> client.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join(),
        "audit log",
        () ->
            client
                .newAuditLogSearchRequest()
                .filter(f -> f.processInstanceKey(String.valueOf(processInstanceKey)))
                .send()
                .join()
                .items());
  }

  private void assertProcessInstanceState(
      final CamundaClient client,
      final long processInstanceKey,
      final ProcessInstanceState expectedState) {
    Awaitility.await("Wait for process instance state: " + expectedState)
        .timeout(TIMEOUT)
        .untilAsserted(
            () -> {
              final var processInstances =
                  client
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join()
                      .items();
              assertThat(processInstances)
                  .hasSize(1)
                  .first()
                  .extracting(ProcessInstance::getState)
                  .isEqualTo(expectedState);
            });
  }

  private long getChildProcessInstanceKey(final CamundaClient client, final long rootPiKey) {
    return Awaitility.await()
        .timeout(TIMEOUT)
        .until(
            () ->
                client
                    .newProcessInstanceSearchRequest()
                    .filter(f -> f.parentProcessInstanceKey(rootPiKey))
                    .send()
                    .join()
                    .items(),
            items -> !items.isEmpty())
        .getFirst()
        .getProcessInstanceKey();
  }

  private JobWorker openJobWorker(final CamundaClient client) {
    return client
        .newWorker()
        .jobType(JOB_TYPE)
        .handler(
            (c, j) -> {
              if (shouldWorkerCompleteJob.get()) {
                // Complete successfully after resolution
                c.newCompleteCommand(j.getKey()).variables(Map.of("jobVar", "done")).send();
              } else {
                c.newFailCommand(j.getKey())
                    .retries(0)
                    .errorMessage("intentional failure")
                    .send()
                    .join();
              }
            })
        .timeout(TIMEOUT)
        .name("worker")
        .maxJobsActive(1)
        .open();
  }

  private void deployResources(final CamundaClient client) {
    final BpmnModelInstance childBpmn =
        Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
            .startEvent()
            .sequenceFlowId("flow1")
            .serviceTask("serviceTask", t -> t.zeebeJobType(JOB_TYPE))
            .userTask("userTask")
            .zeebeUserTask()
            .intermediateCatchEvent(
                "catchEvent",
                c -> c.message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKeyExpression("key")))
            .businessRuleTask(
                "decisionTask",
                t -> t.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable("result"))
            .endEvent()
            .done();
    final BpmnModelInstance rootBpmn =
        Bpmn.createExecutableProcess(ROOT_PROCESS_ID)
            .startEvent()
            .callActivity("callChildProcess", b -> b.zeebeProcessId(CHILD_PROCESS_ID))
            .userTask("rootUserTask")
            .zeebeUserTask()
            .endEvent()
            .done();

    // Simple DMN
    final String dmnXml =
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" id="definitions" name="definitions" namespace="http://camunda.org/schema/1.0/dmn">
              <decision id="%s" name="Jedi or Sith">
                <decisionTable id="decisionTable">
                  <input id="input1" label="Light Saber Color">
                    <inputExpression id="inputExpression1" typeRef="string">
                      <text>color</text>
                    </inputExpression>
                  </input>
                  <output id="output1" label="Alignment" name="alignment" typeRef="string" />
                  <rule id="rule1">
                    <inputEntry id="inputEntry1">
                      <text>"blue"</text>
                    </inputEntry>
                    <outputEntry id="outputEntry1">
                      <text>"Jedi"</text>
                    </outputEntry>
                  </rule>
                </decisionTable>
              </decision>
            </definitions>
            """
            .formatted(DECISION_ID);

    client
        .newDeployResourceCommand()
        .addProcessModel(childBpmn, "child-process.bpmn")
        .addProcessModel(rootBpmn, "root-process.bpmn")
        .addResourceStream(
            new ByteArrayInputStream(dmnXml.getBytes(StandardCharsets.UTF_8)), "decision.dmn")
        .send()
        .join();
  }

  private long startRootProcessInstance(final CamundaClient client) {
    final Map<String, Object> variables = new HashMap<>();
    variables.put("key", "correlationKey");
    variables.put("color", "blue"); // For DMN
    variables.put("initialVar", "value");

    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(ROOT_PROCESS_ID)
        .latestVersion()
        .variables(variables)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private void completeUserTask(final CamundaClient client, final long processInstanceKey) {
    // Wait for user task to appear then complete it
    final var userTaskKey =
        Awaitility.await()
            .atMost(TIMEOUT)
            .ignoreExceptions()
            .until(
                () ->
                    client
                        .newUserTaskSearchRequest()
                        .filter(
                            f ->
                                f.processInstanceKey(processInstanceKey)
                                    .state(UserTaskState.CREATED))
                        .send()
                        .join()
                        .items(),
                tasks -> !tasks.isEmpty())
            .getFirst()
            .getUserTaskKey();
    client
        .newCompleteUserTaskCommand(userTaskKey)
        .variables(Map.of("userTaskVar", "completed"))
        .send()
        .join();
  }

  private void publishMessage(final CamundaClient client, final long processInstanceKey) {
    client
        .newPublishMessageCommand()
        .messageName(MESSAGE_NAME)
        .correlationKey("correlationKey")
        .variables(Map.of("msgVar", "received"))
        .send()
        .join();
    Awaitility.await()
        .until(
            () ->
                !client
                    .newCorrelatedMessageSubscriptionSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey).messageName(MESSAGE_NAME))
                    .send()
                    .join()
                    .items()
                    .isEmpty());
  }

  private void handleIncident(final CamundaClient client, final long processInstanceKey) {
    // Wait for incident
    Awaitility.await()
        .atMost(TIMEOUT)
        .ignoreExceptions()
        .until(
            () -> {
              final var result =
                  client
                      .newIncidentSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              if (result.items().isEmpty()) {
                return false;
              }
              final var incident = result.items().getFirst();
              client.newUpdateJobCommand(incident.getJobKey()).updateRetries(1).send().join();
              shouldWorkerCompleteJob.set(true);
              client.newResolveIncidentCommand(incident.getIncidentKey()).send().join();
              return true;
            });
  }
}
