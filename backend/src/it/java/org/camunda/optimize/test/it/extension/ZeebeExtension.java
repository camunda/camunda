/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.it.extension;

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
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.importing.ZeebeConstants;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded Zeebe Extension
 */
@Slf4j
public class ZeebeExtension implements BeforeEachCallback, AfterEachCallback {

  private static final String ZEEBE_CONFIG_PATH = "zeebe/zeebe-application.yml";
  private static final String ZEEBE_VERSION = IntegrationTestConfigurationUtil.getZeebeDockerVersion();

  private ZeebeContainer zeebeContainer;
  private ZeebeClient zeebeClient;

  @Getter
  private String zeebeRecordPrefix;

  public ZeebeExtension() {
    Testcontainers.exposeHostPorts(9200);
    this.zeebeContainer = new ZeebeContainer(DockerImageName.parse("camunda/zeebe:" + ZEEBE_VERSION))
      .withEnv("ZEEBE_CLOCK_CONTROLLED", "true")
      .withCopyFileToContainer(
        MountableFile.forClasspathResource(ZEEBE_CONFIG_PATH),
        "/usr/local/zeebe/config/application.yml"
      );
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
    zeebeClient =
      ZeebeClient.newClientBuilder()
        .defaultRequestTimeout(Duration.ofMillis(15000))
        .gatewayAddress(zeebeContainer.getExternalGatewayAddress())
        .usePlaintext()
        .build();
  }

  public void destroyClient() {
    zeebeClient.close();
    zeebeClient = null;
  }

  public Process deployProcess(BpmnModelInstance bpmnModelInstance) {
    DeployResourceCommandStep1 deployProcessCommandStep1 = zeebeClient.newDeployResourceCommand();
    deployProcessCommandStep1.addProcessModel(bpmnModelInstance, "resourceName.bpmn");
    final DeploymentEvent deploymentEvent =
      ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
        .send()
        .join();
    return deploymentEvent.getProcesses().get(0);
  }

  public long startProcessInstanceWithVariables(String bpmnProcessId, Map<String, Object> variables) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 createProcessInstanceCommandStep3 =
      zeebeClient
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .variables(variables);
    return createProcessInstanceCommandStep3.send().join().getProcessInstanceKey();
  }

  public void addVariablesToScope(Long variableScopeKey, Map<String, Object> variables, boolean local) {
    zeebeClient
      .newSetVariablesCommand(variableScopeKey)
      .variables(variables)
      .local(local)
      .send()
      .join();
  }

  public void setClock(Instant pinAt) throws IOException, InterruptedException {
    final ClockActuatorClient clockClient = new ClockActuatorClient(zeebeContainer.getExternalMonitoringAddress());
    clockClient.pinZeebeTime(pinAt);
  }

  public ProcessInstanceEvent startProcessInstanceForProcess(String processId) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 startInstanceCommand =
      zeebeClient.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion();
    return startInstanceCommand.send().join();
  }

  public void cancelProcessInstance(long processInstanceKey) {
    zeebeClient.newCancelInstanceCommand(processInstanceKey).send().join();
  }

  @SneakyThrows
  public void completeTaskForInstanceWithJobType(String jobType) {
    completeTaskForInstanceWithJobType(jobType, null);
  }

  @SneakyThrows
  public void completeTaskForInstanceWithJobType(String jobType, Map<String, Object> variables) {
    handleSingleJob(jobType, (zeebeClient, job) -> {
      CompleteJobCommandStep1 completeJobCommandStep1 = zeebeClient.newCompleteCommand(job.getKey());
      Optional.ofNullable(variables).ifPresent(completeJobCommandStep1::variables);
      completeJobCommandStep1.send().join();
    });
  }

  public void throwErrorIncident(String jobType) {
    handleSingleJob(jobType, (zeebeClient, job) -> {
      zeebeClient.newThrowErrorCommand(job.getKey())
        .errorCode("1")
        .errorMessage("someErrorMessage")
        .send().join();
    });
  }

  public void failTask(String jobType) {
    handleSingleJob(jobType, (zeebeClient, job) -> {
      zeebeClient.newFailCommand(job.getKey())
        .retries(0)
        .errorMessage("someTaskFailMessage")
        .send()
        .join();
    });
  }

  public void resolveIncident(Long jobKey, Long incidentKey) {
    zeebeClient.newResolveIncidentCommand(incidentKey).send().join();
  }

  private void handleSingleJob(String jobType, JobHandler jobHandler) {
    AtomicBoolean jobCompleted = new AtomicBoolean(false);
    JobWorker jobWorker = zeebeClient.newWorker()
      .jobType(jobType)
      .handler((zeebeClient, type) -> {
        if (jobCompleted.compareAndSet(false, true)) {
          jobHandler.handle(zeebeClient, type);
        } else {
          zeebeClient.newFailCommand(type.getKey())
            .retries(type.getRetries())
            .errorMessage("skip job handling, already handled in this way")
            .send()
            .join();
        }
      })
      .timeout(Duration.ofSeconds(2))
      .open();
    Awaitility.await()
      .timeout(10, TimeUnit.SECONDS)
      .untilTrue(jobCompleted);
    jobWorker.close();
  }

  private void setZeebeRecordPrefixForTest() {
    this.zeebeContainer = zeebeContainer.withEnv(
      "ZEEBE_BROKER_EXPORTERS_OPTIMIZE_ARGS_INDEX_PREFIX",
      zeebeRecordPrefix
    );
  }

}
