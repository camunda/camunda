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

import io.camunda.optimize.service.util.IdGenerator;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.importing.ZeebeConstants;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.net.URI;
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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/** Embedded Zeebe Extension */
public class ZeebeExtension implements BeforeEachCallback, AfterEachCallback {

  private static final String ZEEBE_CONFIG_PATH = "zeebe/zeebe-application.yml";
  private static final String ZEEBE_VERSION =
      IntegrationTestConfigurationUtil.getZeebeDockerVersion();
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ZeebeExtension.class);

  private ZeebeContainer zeebeContainer;
  private ZeebeClient zeebeClient;

  private String zeebeRecordPrefix;

  public ZeebeExtension() {
    final int databasePort;
    final String zeebeExporterClassName;
    if (IntegrationTestConfigurationUtil.getDatabaseType().equals(DatabaseType.OPENSEARCH)) {
      databasePort = 9205;
      zeebeExporterClassName = ZEEBE_OPENSEARCH_EXPORTER;
    } else {
      databasePort = 9200;
      zeebeExporterClassName = ZEEBE_ELASTICSEARCH_EXPORTER;
    }
    Testcontainers.exposeHostPorts(databasePort);
    zeebeContainer =
        new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + ZEEBE_VERSION))
            .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
            .withEnv("DATABASE_PORT", String.valueOf(databasePort))
            .withEnv("ZEEBE_EXPORTER_CLASS_NAME", zeebeExporterClassName)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource(ZEEBE_CONFIG_PATH),
                "/usr/local/zeebe/config/application.yml");
    if (!isZeebeVersionPre85()) {
      zeebeContainer =
          zeebeContainer
              .withEnv("ZEEBE_BROKER_GATEWAY_ENABLE", "true")
              .withAdditionalExposedPort(8080);
    }
  }

  @Override
  public void beforeEach(final ExtensionContext extensionContext) {
    zeebeRecordPrefix = ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX + "-" + IdGenerator.getNextId();
    setZeebeRecordPrefixForTest();
    zeebeContainer.start();
    createClient();
  }

  @Override
  public void afterEach(final ExtensionContext extensionContext) {
    zeebeContainer.stop();
    destroyClient();
  }

  public void createClient() {
    if (isZeebeVersionPre85()) {
      zeebeClient =
          ZeebeClient.newClientBuilder()
              .defaultRequestTimeout(Duration.ofMillis(15000))
              .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
              .usePlaintext()
              .build();
    } else {
      zeebeClient =
          ZeebeClient.newClientBuilder()
              .defaultRequestTimeout(Duration.ofMillis(15000))
              .grpcAddress(
                  URI.create(
                      String.format("http://%s", zeebeContainer.getExternalGatewayAddress())))
              .restAddress(
                  URI.create(String.format("http://%s", zeebeContainer.getExternalAddress(8080))))
              .usePlaintext()
              .build();
    }
  }

  public Process deployProcess(final BpmnModelInstance bpmnModelInstance) {
    final DeployResourceCommandStep1 deployResourceCommandStep1 =
        zeebeClient.newDeployResourceCommand();
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
            zeebeClient
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
    zeebeClient.newBroadcastSignalCommand().signalName(signalName).send().join();
  }

  public void startProcessInstanceBeforeElementWithIds(
      final String bpmnProcessId, final String... elementIds) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    for (final String elementId : elementIds) {
      createProcessInstanceCommandStep3.startBeforeElement(elementId);
    }
    createProcessInstanceCommandStep3.send().join().getProcessInstanceKey();
  }

  public void addVariablesToScope(
      final Long variableScopeKey, final Map<String, Object> variables, final boolean local) {
    zeebeClient
        .newSetVariablesCommand(variableScopeKey)
        .variables(variables)
        .local(local)
        .send()
        .join();
  }

  public void setClock(final Instant pinAt) throws IOException, InterruptedException {
    final ClockActuatorClient clockClient =
        new ClockActuatorClient(zeebeContainer.getExternalMonitoringAddress());
    clockClient.pinZeebeTime(pinAt);
  }

  public ProcessInstanceEvent startProcessInstanceForProcess(final String processId) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 startInstanceCommand =
        zeebeClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion();
    return startInstanceCommand.send().join();
  }

  public void cancelProcessInstance(final long processInstanceKey) {
    zeebeClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  public void completeTaskForInstanceWithJobType(final String jobType) {
    completeTaskForInstanceWithJobType(jobType, null);
  }

  public void completeTaskForInstanceWithJobType(
      final String jobType, final Map<String, Object> variables) {
    handleSingleJob(
        jobType,
        (zeebeClient, job) -> {
          final CompleteJobCommandStep1 completeJobCommandStep1 =
              zeebeClient.newCompleteCommand(job.getKey());
          Optional.ofNullable(variables).ifPresent(completeJobCommandStep1::variables);
          completeJobCommandStep1.send().join();
        });
  }

  public void completeZeebeUserTask(final long userTaskKey) {
    zeebeClient.newUserTaskCompleteCommand(userTaskKey).send().join();
  }

  public void assignUserTask(final long userTaskKey, final String assigneeId) {
    zeebeClient.newUserTaskAssignCommand(userTaskKey).assignee(assigneeId).send().join();
  }

  public void unassignUserTask(final long userTaskKey) {
    zeebeClient.newUserTaskUnassignCommand(userTaskKey).send().join();
  }

  public void updateCandidateGroupForUserTask(final long userTaskKey, final String candidateGroup) {
    zeebeClient.newUserTaskUpdateCommand(userTaskKey).candidateGroups(candidateGroup).send().join();
  }

  public void throwErrorIncident(final String jobType) {
    handleSingleJob(
        jobType,
        (zeebeClient, job) ->
            zeebeClient
                .newThrowErrorCommand(job.getKey())
                .errorCode("1")
                .errorMessage("someErrorMessage")
                .send()
                .join());
  }

  public void failTask(final String jobType) {
    handleSingleJob(
        jobType,
        (zeebeClient, job) ->
            zeebeClient
                .newFailCommand(job.getKey())
                .retries(0)
                .errorMessage("someTaskFailMessage")
                .send()
                .join());
  }

  public void resolveIncident(final Long incidentKey) {
    zeebeClient.newResolveIncidentCommand(incidentKey).send().join();
  }

  private void handleSingleJob(final String jobType, final JobHandler jobHandler) {
    final AtomicBoolean jobCompleted = new AtomicBoolean(false);
    final JobWorker jobWorker =
        zeebeClient
            .newWorker()
            .jobType(jobType)
            .handler(
                (zeebeClient, type) -> {
                  if (jobCompleted.compareAndSet(false, true)) {
                    jobHandler.handle(zeebeClient, type);
                  } else {
                    zeebeClient
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

  private void setZeebeRecordPrefixForTest() {
    zeebeContainer =
        zeebeContainer.withEnv(
            "ZEEBE_BROKER_EXPORTERS_OPTIMIZE_ARGS_INDEX_PREFIX", zeebeRecordPrefix);
  }

  private void destroyClient() {
    zeebeClient.close();
    zeebeClient = null;
  }

  public String getZeebeRecordPrefix() {
    return zeebeRecordPrefix;
  }
}
