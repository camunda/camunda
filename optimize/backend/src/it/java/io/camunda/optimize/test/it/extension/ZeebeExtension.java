/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.it.extension;

import static io.camunda.optimize.AbstractCCSMIT.isZeebeVersionPre85;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_ELASTICSEARCH_EXPORTER;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_OPENSEARCH_EXPORTER;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceReference;
import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.testcontainers.Testcontainers;

/** Embedded Zeebe Extension */
public class ZeebeExtension implements BeforeEachCallback, AfterEachCallback {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ZeebeExtension.class);

  private final TestStandaloneBroker standaloneBroker;
  private CamundaClient camundaClient;

  private String zeebeRecordPrefix;

  public ZeebeExtension() {

    System.setProperty("management.endpoints.web.exposure.include", "*");

    standaloneBroker =
        new TestStandaloneBroker()
            .withAdditionalProperties(
                Map.of(
                    "zeebe.log.level",
                    "ERROR",
                    "atomix.log.level",
                    "ERROR",
                    "zeebe.clock.controlled",
                    "true",
                    "zeebe.broker.gateway.enable",
                    "true"))
            // Enable basic auth so that consolidated-auth triggers the spring security registration
            // so that they don't get auto-configured by Spring as it hides the actuator endpoints
            // behind authentication.
            .withBasicAuth()
            .withUnauthenticatedAccess()
            .withAuthorizationsDisabled();
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    zeebeRecordPrefix = ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX + "-" + IdGenerator.getNextId();
    final var dbType = IntegrationTestConfigurationUtil.getDatabaseType();
    final String zeebeExporterClassName;

    if (dbType.equals(DatabaseType.OPENSEARCH)) {
      zeebeExporterClassName = ZEEBE_OPENSEARCH_EXPORTER;
    } else {
      zeebeExporterClassName = ZEEBE_ELASTICSEARCH_EXPORTER;
    }
    Testcontainers.exposeHostPorts(9200);

    standaloneBroker
        .withUnifiedConfig(
            cfg -> {
              cfg.getCluster().setPartitionCount(2);
              cfg.getData()
                  .getSecondaryStorage()
                  .setType(SecondaryStorageType.valueOf(dbType.getId()));
              if (dbType.equals(DatabaseType.OPENSEARCH)) {
                cfg.getData().getSecondaryStorage().getOpensearch().setUrl("http://localhost:9200");
                cfg.getData()
                    .getSecondaryStorage()
                    .getOpensearch()
                    .setIndexPrefix(zeebeRecordPrefix);
              } else {
                cfg.getData()
                    .getSecondaryStorage()
                    .getElasticsearch()
                    .setUrl("http://localhost:9200");
                cfg.getData()
                    .getSecondaryStorage()
                    .getElasticsearch()
                    .setIndexPrefix(zeebeRecordPrefix);
              }
            })
        .withExporter(
            dbType.getId() + "exporter",
            cfg -> {
              cfg.setClassName(zeebeExporterClassName);
              cfg.setArgs(
                  Map.of(
                      "index",
                      Map.of("prefix", zeebeRecordPrefix),
                      "bulk",
                      Map.of("size", 1),
                      "connect",
                      Map.of(
                          "url",
                          "http://localhost:9200",
                          "indexPrefix",
                          zeebeRecordPrefix,
                          "type",
                          dbType.toString())));
            })
        .withCreateSchema(true)
        .withAdditionalProperties(
            Map.of(
                "camunda.data.secondary-storage.type",
                dbType.getId(),
                "camunda.database.type",
                dbType.getId(),
                "camunda.database.url",
                "http://localhost:9200",
                "camunda.tasklist.database",
                dbType.getId(),
                "camunda.operate.database",
                dbType.getId(),
                "camunda.tasklist." + dbType.getId() + ".url",
                "http://localhost:9200",
                "camunda.operate." + dbType.getId() + ".url",
                "http://localhost:9200"))
        .start();
    createClient();
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    standaloneBroker.stop();
    destroyClient();
  }

  public void createClient() {
    if (isZeebeVersionPre85()) {
      camundaClient =
          CamundaClient.newClientBuilder()
              .defaultRequestTimeout(Duration.ofMillis(15000))
              .grpcAddress(standaloneBroker.grpcAddress())
              .build();
    } else {
      camundaClient =
          CamundaClient.newClientBuilder()
              .preferRestOverGrpc(false)
              .defaultRequestTimeout(Duration.ofMillis(15000))
              .grpcAddress(standaloneBroker.grpcAddress())
              .restAddress(standaloneBroker.restAddress())
              .build();
    }
  }

  public Process deployProcess(final BpmnModelInstance bpmnModelInstance) {
    final DeployResourceCommandStep1 deployResourceCommandStep1 =
        camundaClient.newDeployResourceCommand();
    deployResourceCommandStep1.addProcessModel(bpmnModelInstance, "resourceName.bpmn");
    final DeploymentEvent deploymentEvent =
        ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployResourceCommandStep1)
            .send()
            .join();
    return deploymentEvent.getProcesses().get(0);
  }

  public long startProcessInstanceWithVariables(
      final String bpmnProcessId, final Map<String, Object> variables) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            camundaClient
                .newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .variables(variables);
    return createProcessInstanceCommandStep3.send().join().getProcessInstanceKey();
  }

  public void startProcessInstanceWithSignal(final String signalName) {
    broadcastSignalWithName(signalName);
  }

  public void broadcastSignalWithName(final String signalName) {
    camundaClient.newBroadcastSignalCommand().signalName(signalName).send().join();
  }

  public long startProcessInstanceByConditionalEvaluation(
      final long processDefinitionKey, final Map<String, Object> variables) {
    return camundaClient
        .newEvaluateConditionalCommand()
        .variables(variables)
        .processDefinitionKey(processDefinitionKey)
        .send()
        .join()
        .getProcessInstances()
        .stream()
        .findFirst()
        .map(ProcessInstanceReference::getProcessInstanceKey)
        .orElseThrow(
            () ->
                new RuntimeException(
                    "No process instance created by conditional evaluation for process definition key: "
                        + processDefinitionKey));
  }

  public void startProcessInstanceBeforeElementWithIds(
      final String bpmnProcessId, final String... elementIds) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            camundaClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    for (final String elementId : elementIds) {
      createProcessInstanceCommandStep3.startBeforeElement(elementId);
    }
    createProcessInstanceCommandStep3.send().join().getProcessInstanceKey();
  }

  public void addVariablesToScope(
      final Long variableScopeKey, final Map<String, Object> variables, final boolean local) {
    camundaClient
        .newSetVariablesCommand(variableScopeKey)
        .variables(variables)
        .local(local)
        .send()
        .join();
  }

  public void setClock(final Instant pinAt) throws IOException, InterruptedException {
    final ClockActuatorClient clockClient =
        new ClockActuatorClient(standaloneBroker.address(TestZeebePort.MONITORING));
    clockClient.pinZeebeTime(pinAt);
  }

  public ProcessInstanceEvent startProcessInstanceForProcess(final String processId) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 startInstanceCommand =
        camundaClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion();
    return startInstanceCommand.send().join();
  }

  public void cancelProcessInstance(final long processInstanceKey) {
    camundaClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  public void completeTaskForInstanceWithJobType(final String jobType) {
    completeTaskForInstanceWithJobType(jobType, null);
  }

  public void completeTaskForInstanceWithJobType(
      final String jobType, final Map<String, Object> variables) {
    handleSingleJob(
        jobType,
        (camundaClient, job) -> {
          final CompleteJobCommandStep1 completeJobCommandStep1 =
              camundaClient.newCompleteCommand(job.getKey());
          Optional.ofNullable(variables).ifPresent(completeJobCommandStep1::variables);
          completeJobCommandStep1.send().join();
        });
  }

  public void completeZeebeUserTask(final long userTaskKey) {
    camundaClient.newCompleteUserTaskCommand(userTaskKey).send().join();
  }

  public void assignUserTask(final long userTaskKey, final String assigneeId) {
    camundaClient.newAssignUserTaskCommand(userTaskKey).assignee(assigneeId).send().join();
  }

  public void unassignUserTask(final long userTaskKey) {
    camundaClient.newUnassignUserTaskCommand(userTaskKey).send().join();
  }

  public void updateCandidateGroupForUserTask(final long userTaskKey, final String candidateGroup) {
    camundaClient
        .newUpdateUserTaskCommand(userTaskKey)
        .candidateGroups(candidateGroup)
        .send()
        .join();
  }

  public void throwErrorIncident(final String jobType) {
    handleSingleJob(
        jobType,
        (camundaClient, job) ->
            camundaClient
                .newThrowErrorCommand(job.getKey())
                .errorCode("1")
                .errorMessage("someErrorMessage")
                .send()
                .join());
  }

  public void failTask(final String jobType) {
    handleSingleJob(
        jobType,
        (camundaClient, job) ->
            camundaClient
                .newFailCommand(job.getKey())
                .retries(0)
                .errorMessage("someTaskFailMessage")
                .send()
                .join());
  }

  public void resolveIncident(final Long incidentKey) {
    camundaClient.newResolveIncidentCommand(incidentKey).send().join();
  }

  public String getZeebeRecordPrefix() {
    return zeebeRecordPrefix;
  }

  private void handleSingleJob(final String jobType, final JobHandler jobHandler) {
    final AtomicBoolean jobCompleted = new AtomicBoolean(false);
    final JobWorker jobWorker =
        camundaClient
            .newWorker()
            .jobType(jobType)
            .handler(
                (camundaClient, type) -> {
                  if (jobCompleted.compareAndSet(false, true)) {
                    jobHandler.handle(camundaClient, type);
                  } else {
                    camundaClient
                        .newFailCommand(type.getKey())
                        .retries(type.getRetries())
                        .errorMessage("skip job handling, already handled in this way")
                        .send()
                        .join();
                  }
                })
            .timeout(Duration.ofSeconds(2))
            .open();
    Awaitility.await().timeout(10, TimeUnit.SECONDS).untilTrue(jobCompleted);
    jobWorker.close();
  }

  private void destroyClient() {
    if (camundaClient != null) {
      camundaClient.close();
      camundaClient = null;
    }
  }
}
