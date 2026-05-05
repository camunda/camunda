/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import org.awaitility.Awaitility;

public final class OperateDataGenerator extends AbstractBackupDataGenerator {

  static final String PROCESS_BPMN_PROCESS_ID = "basicProcess";
  static final int PROCESS_INSTANCE_COUNT = 51;
  static final int INCIDENT_COUNT = 32;
  static final int COUNT_OF_CANCEL_OPERATION = 9;
  static final int COUNT_OF_RESOLVE_OPERATION = 8;
  static final String NEW_BPMN_PROCESS_ID = "testProcess2";
  static final int CANCELLED_PROCESS_INSTANCES = 3;
  static final int NEW_PROCESS_INSTANCES_COUNT = 13;
  private static final int MIN_ACTIVE_PROCESS_INSTANCE_COUNT_AFTER_INITIAL_OPERATIONS =
      PROCESS_INSTANCE_COUNT - COUNT_OF_CANCEL_OPERATION;
  private static final int MIN_ACTIVE_PROCESS_INSTANCE_COUNT_AFTER_CHANGE =
      MIN_ACTIVE_PROCESS_INSTANCE_COUNT_AFTER_INITIAL_OPERATIONS
          + NEW_PROCESS_INSTANCES_COUNT
          - CANCELLED_PROCESS_INSTANCES;

  private static final int SEQUENCE_FLOW_COUNT_PER_PROCESS_INSTANCE = 2;
  private static final int MAX_RETRY_ATTEMPTS = 100;

  private final Random random = new Random();
  private List<Long> processInstanceKeys = new ArrayList<>();

  public OperateDataGenerator(final CamundaClient camundaClient) {
    super(camundaClient);
  }

  @Override
  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

    deployProcess(PROCESS_BPMN_PROCESS_ID, createModel(PROCESS_BPMN_PROCESS_ID));
    processInstanceKeys = startProcessInstances(PROCESS_BPMN_PROCESS_ID, PROCESS_INSTANCE_COUNT);
    completeTasks("task1");
    createIncidents("task2");

    waitUntilAllDataAreImported();

    for (int i = 0; i < COUNT_OF_CANCEL_OPERATION; i++) {
      createCancelOperation(processInstanceKeys.size() * 10);
    }
    logger.info("{} operations of type CANCEL_PROCESS_INSTANCE started", COUNT_OF_CANCEL_OPERATION);

    for (int i = 0; i < COUNT_OF_RESOLVE_OPERATION; i++) {
      createResolveIncidentOperation(processInstanceKeys.size() * 10);
    }
    logger.info("{} operations of type RESOLVE_INCIDENT started", COUNT_OF_RESOLVE_OPERATION);

    logger.info(
        "Data generation completed in: {} s",
        ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
  }

  @Override
  public void assertData() {
    Awaitility.await("should expose the original backup data")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(this::assertDataOneAttempt);
  }

  @Override
  public void changeData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting changing the data...");

    deployProcess(NEW_BPMN_PROCESS_ID, createModel(NEW_BPMN_PROCESS_ID));
    startProcessInstances(NEW_BPMN_PROCESS_ID, NEW_PROCESS_INSTANCES_COUNT);

    for (int i = 0; i < 10; i++) {
      createOperationFromIncidentProcessInstances(this::resolveIncidents, "RESOLVE_INCIDENT");
    }

    for (int i = 0; i < CANCELLED_PROCESS_INSTANCES; i++) {
      createOperationFromIncidentProcessInstances(this::cancelInstance, "CANCEL_PROCESS_INSTANCE");
    }

    logger.info(
        "Data changing completed in: {} s",
        ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
  }

  @Override
  public void assertDataAfterChange() {
    Awaitility.await("should expose the changed data")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final List<ProcessInstance> processInstances =
                  searchAllRelevantActiveProcessInstances();
              assertThat(
                      processInstances.stream()
                          .map(ProcessInstance::getProcessDefinitionId)
                          .distinct()
                          .toList())
                  .containsExactlyInAnyOrder(PROCESS_BPMN_PROCESS_ID, NEW_BPMN_PROCESS_ID);
              assertThat(processInstances)
                  .hasSizeBetween(
                      MIN_ACTIVE_PROCESS_INSTANCE_COUNT_AFTER_CHANGE,
                      PROCESS_INSTANCE_COUNT
                          + NEW_PROCESS_INSTANCES_COUNT
                          - CANCELLED_PROCESS_INSTANCES);
            });
  }

  private void assertDataOneAttempt() {
    final List<ProcessInstance> processInstances =
        searchProcessInstances(PROCESS_BPMN_PROCESS_ID, ProcessInstanceState.ACTIVE);
    assertThat(processInstances)
        .extracting(ProcessInstance::getProcessDefinitionId)
        .containsOnly(PROCESS_BPMN_PROCESS_ID);
    assertThat(processInstances)
        .hasSizeBetween(
            MIN_ACTIVE_PROCESS_INSTANCE_COUNT_AFTER_INITIAL_OPERATIONS, PROCESS_INSTANCE_COUNT);

    for (int i = 0; i < 10; i++) {
      final var sequenceFlows =
          camundaClient
              .newProcessInstanceSequenceFlowsRequest(chooseProcessInstanceKey(processInstances))
              .send()
              .join();
      assertThat(sequenceFlows).hasSize(SEQUENCE_FLOW_COUNT_PER_PROCESS_INSTANCE);
    }

    final List<ProcessInstance> incidentProcessInstances = searchIncidentProcessInstances();
    assertThat(incidentProcessInstances)
        .hasSizeBetween(
            INCIDENT_COUNT - (COUNT_OF_CANCEL_OPERATION + COUNT_OF_RESOLVE_OPERATION),
            INCIDENT_COUNT);
  }

  private void waitUntilAllDataAreImported() {
    logger.info("Wait until all data is imported.");
    Awaitility.await("should import all active process instances")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () ->
                assertThat(
                        searchProcessInstances(
                            PROCESS_BPMN_PROCESS_ID, ProcessInstanceState.ACTIVE))
                    .hasSize(PROCESS_INSTANCE_COUNT));
  }

  private void createCancelOperation(final int maxAttempts) {
    createOperation(maxAttempts, this::cancelInstance, "CANCEL_PROCESS_INSTANCE");
  }

  private void createResolveIncidentOperation(final int maxAttempts) {
    createOperation(maxAttempts, this::resolveIncidents, "RESOLVE_INCIDENT");
  }

  private void createOperationFromIncidentProcessInstances(
      final LongConsumer operation, final String operationName) {
    logger.debug(
        "Try to create change operation {} against incident instances ({} attempts)",
        operationName,
        MAX_RETRY_ATTEMPTS);

    boolean operationStarted = false;
    int attempts = 0;
    while (!operationStarted && attempts < MAX_RETRY_ATTEMPTS) {
      final List<ProcessInstance> incidentProcessInstances = searchIncidentProcessInstances();
      if (incidentProcessInstances.isEmpty()) {
        logger.debug(
            "Skip change operation {} because no incident process instances were found",
            operationName);
        return;
      }

      try {
        operation.accept(chooseProcessInstanceKey(incidentProcessInstances));
        operationStarted = true;
      } catch (final RuntimeException e) {
        logger.debug(
            "Failed to create change operation {} on attempt {}", operationName, attempts + 1, e);
      }
      attempts++;
    }

    assertThat(operationStarted)
        .as("change operation %s should be started within %s attempts", operationName, 100)
        .isTrue();
  }

  private void createOperation(
      final int maxAttempts, final LongConsumer operation, final String operationName) {
    logger.debug("Try to create operation {} ({} attempts)", operationName, maxAttempts);
    boolean operationStarted = false;
    int attempts = 0;
    while (!operationStarted && attempts < maxAttempts) {
      try {
        operation.accept(chooseStoredProcessInstanceKey(processInstanceKeys));
        operationStarted = true;
      } catch (final RuntimeException e) {
        logger.debug("Failed to create operation {} on attempt {}", operationName, attempts + 1, e);
      }
      attempts++;
    }

    assertThat(operationStarted)
        .as("operation %s should be started within %s attempts", operationName, maxAttempts)
        .isTrue();
  }

  private void createIncidents(final String jobType) {
    final Set<Long> failedJobKeys = ConcurrentHashMap.newKeySet();

    Awaitility.await("should create incidents")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final int remaining = INCIDENT_COUNT - failedJobKeys.size();
              if (remaining <= 0) {
                assertThat(failedJobKeys).hasSize(INCIDENT_COUNT);
                return;
              }

              final var jobs =
                  camundaClient
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(remaining)
                      .send()
                      .join()
                      .getJobs();

              jobs.forEach(
                  job -> {
                    if (failedJobKeys.add(job.getKey())) {
                      camundaClient
                          .newFailCommand(job.getKey())
                          .retries(0)
                          .errorMessage("fail job")
                          .send()
                          .join();
                    }
                  });

              assertThat(failedJobKeys).hasSize(INCIDENT_COUNT);
            });

    logger.info("{} incidents in {} created", INCIDENT_COUNT, jobType);
  }

  private void completeTasks(final String jobType) {
    final Set<Long> completedJobKeys = ConcurrentHashMap.newKeySet();

    Awaitility.await("should complete tasks")
        .atMost(DATA_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final int remaining = PROCESS_INSTANCE_COUNT - completedJobKeys.size();
              if (remaining <= 0) {
                assertThat(completedJobKeys).hasSize(PROCESS_INSTANCE_COUNT);
                return;
              }

              final var jobs =
                  camundaClient
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(remaining)
                      .send()
                      .join()
                      .getJobs();

              jobs.forEach(
                  job -> {
                    if (completedJobKeys.add(job.getKey())) {
                      camundaClient
                          .newCompleteCommand(job.getKey())
                          .variable("varOut", "value2")
                          .send()
                          .join();
                    }
                  });

              assertThat(completedJobKeys).hasSize(PROCESS_INSTANCE_COUNT);
            });

    logger.info("{} tasks {} completed", PROCESS_INSTANCE_COUNT, jobType);
  }

  private static BpmnModelInstance createModel(final String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .serviceTask("task1")
        .zeebeJobType("task1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
        .serviceTask("task2")
        .zeebeJobType("task2")
        .serviceTask("task3")
        .zeebeJobType("task3")
        .serviceTask("task4")
        .zeebeJobType("task4")
        .serviceTask("task5")
        .zeebeJobType("task5")
        .endEvent()
        .done();
  }

  private List<ProcessInstance> searchAllRelevantActiveProcessInstances() {
    final List<ProcessInstance> all = new ArrayList<>();
    all.addAll(searchProcessInstances(PROCESS_BPMN_PROCESS_ID, ProcessInstanceState.ACTIVE));
    all.addAll(searchProcessInstances(NEW_BPMN_PROCESS_ID, ProcessInstanceState.ACTIVE));
    return all;
  }

  private List<ProcessInstance> searchIncidentProcessInstances() {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(f -> f.state(ProcessInstanceState.ACTIVE).hasIncident(true))
        .page(p -> p.limit(SEARCH_LIMIT).from(0))
        .send()
        .join()
        .items();
  }

  private void resolveIncidents(final long processInstanceKey) {
    camundaClient.newResolveProcessInstanceIncidentsCommand(processInstanceKey).send().join();
  }

  private void cancelInstance(final long processInstanceKey) {
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  private long chooseStoredProcessInstanceKey(final List<Long> keys) {
    return keys.get(random.nextInt(keys.size()));
  }

  private long chooseProcessInstanceKey(final List<ProcessInstance> processInstances) {
    return processInstances.get(random.nextInt(processInstances.size())).getProcessInstanceKey();
  }
}
