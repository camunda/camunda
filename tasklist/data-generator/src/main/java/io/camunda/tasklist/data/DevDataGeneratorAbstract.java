/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static io.camunda.zeebe.protocol.record.value.TenantOwned.DEFAULT_TENANT_IDENTIFIER;

import io.camunda.client.impl.command.StreamUtil;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.PayloadUtil;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class DevDataGeneratorAbstract implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorAbstract.class);

  @Autowired protected TasklistProperties tasklistProperties;

  @Autowired private PayloadUtil payloadUtil;

  @Autowired private ResourceServices resourceServices;

  @Autowired private ProcessInstanceServices processInstanceServices;

  @Autowired private CamundaAuthenticationProvider authenticationProvider;

  private final Random random = new Random();

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  private boolean shutdown = false;

  @PostConstruct
  private void startDataGenerator() {
    startGeneratingData();
  }

  protected void startGeneratingData() {
    LOGGER.debug("INIT: Generate demo data...");
    try {
      createZeebeDataAsync();
    } catch (final Exception ex) {
      LOGGER.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      LOGGER.error("Error occurred when generating demo data.", ex);
    }
  }

  @Override
  public void createZeebeDataAsync() {
    if (shouldCreateData()) {
      executor.submit(
          () -> {
            boolean created = false;
            while (!created && !shutdown) {
              try {
                Thread.sleep(10_000);
                createZeebeData();
                created = true;
              } catch (final Exception ex) {
                LOGGER.error("Demo data was not generated, will retry", ex);
              }
            }
          });
    }
  }

  private void createZeebeData() {
    deployProcesses();
    startProcessInstances();
  }

  private void startProcessInstances() {
    final int instancesCount = random.nextInt(20) + 20;
    for (int i = 0; i < instancesCount; i++) {
      startOrderProcess();
      startFlightRegistrationProcess();
      startSimpleProcess();
      startBigFormProcess();
      startCarForRentProcess();
      startTwoUserTasks();
    }
  }

  private void startSimpleProcess() {
    String payload = null;
    final int choice = random.nextInt(3);
    if (choice == 0) {
      payload =
          "{\"stringVar\":\"varValue"
              + random.nextInt(100)
              + "\", "
              + " \"intVar\": 123, "
              + " \"boolVar\": true, "
              + " \"emptyStringVar\": \"\", "
              + " \"objectVar\": "
              + "   {\"testVar\":555, \n"
              + "   \"testVar2\": \"dkjghkdg\"}}";
    } else if (choice == 1) {
      payload = payloadUtil.readJSONStringFromClasspath("/large-payload.json");
    }
    startProcessInstance("simpleProcess", payload);
  }

  private void startBigFormProcess() {
    startProcessInstance("bigFormProcess", null);
  }

  private void startCarForRentProcess() {
    startProcessInstance("registerCarForRent", null);
  }

  private void startTwoUserTasks() {
    startProcessInstance("twoUserTasks", null);
  }

  private void startMultipleVersionsProcess() {
    startProcessInstance("multipleVersions", null);
  }

  private void startOrderProcess() {
    final float price1 = Math.round(random.nextFloat() * 100000) / 100;
    final float price2 = Math.round(random.nextFloat() * 10000) / 100;
    final String payload =
        "{\n"
            + "  \"clientNo\": \"CNT-1211132-02\",\n"
            + "  \"orderNo\": \"CMD0001-01\",\n"
            + "  \"items\": [\n"
            + "    {\n"
            + "      \"code\": \"123.135.625\",\n"
            + "      \"name\": \"Laptop Lenovo ABC-001\",\n"
            + "      \"quantity\": 1,\n"
            + "      \"price\": "
            + Double.valueOf(price1)
            + "\n"
            + "    },\n"
            + "    {\n"
            + "      \"code\": \"111.653.365\",\n"
            + "      \"name\": \"Headset Sony QWE-23\",\n"
            + "      \"quantity\": 2,\n"
            + "      \"price\": "
            + Double.valueOf(price2)
            + "\n"
            + "    }\n"
            + "  ],\n"
            + "  \"mwst\": "
            + Double.valueOf((price1 + price2) * 0.19)
            + ",\n"
            + "  \"total\": "
            + Double.valueOf((price1 + price2))
            + ",\n"
            + "  \"orderStatus\": \"NEW\"\n"
            + "}";
    startProcessInstance("orderProcess", payload);
  }

  private void startFlightRegistrationProcess() {
    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    final Calendar calendar = Calendar.getInstance();
    calendar.setTime(new Date());

    calendar.add(Calendar.DATE, 5);
    final String dueDate = sdf.format(calendar.getTime());

    calendar.add(Calendar.DATE, 1);
    final String followUpDate = sdf.format(calendar.getTime());

    final String payload =
        "{\"candidateGroups\": [\"group1\", \"group2\"],"
            + "\"assignee\": \"demo\", "
            + "\"taskDueDate\" : \""
            + dueDate
            + "\", "
            + "\"taskFollowUpDate\" : \""
            + followUpDate
            + "\"}";

    startProcessInstance("flightRegistration", payload);
  }

  private void startProcessInstance(final String processId, final String payload) {
    final Map<String, Object> variables =
        payload != null && !payload.isEmpty() ? payloadUtil.parsePayload(payload) : Map.of();
    final Runnable runCreateProcessInstance =
        () ->
            createProcessInstanceWithoutAuthentication(
                processId, variables, DEFAULT_TENANT_IDENTIFIER);
    try {
      runCreateProcessInstance.run();
    } catch (final Exception ex) {
      // retry once
      sleepFor(300L);
      runCreateProcessInstance.run();
    }
    LOGGER.debug("Process instance created for process [{}]", processId);
  }

  private void deployProcesses() {
    // Deploy Forms
    deployResource("formDeployedV1.form");
    deployResource("formDeployedV2.form");
    deployResource("bigForm.form");
    deployResource("checkPayment.form");
    deployResource("doTaskA.form");
    deployResource("doTaskB.form");
    deployResource("humanTaskForm.form");
    deployResource("registerCabinBag.form");
    deployResource("registerCarForRent.form");
    deployResource("registerThePassenger.form");

    // Deploy Processes
    deployResource("startedByLinkedForm.bpmn");
    deployResource("formIdProcessDeployed.bpmn");
    deployResource("orderProcess.bpmn");
    deployResource("registerPassenger.bpmn");
    deployResource("simpleProcess.bpmn");
    deployResource("bigFormProcess.bpmn");
    deployResource("registerCarForRent.bpmn");
    deployResource("twoUserTasks.bpmn");
    deployResource("multipleVersions.bpmn");
    deployResource("multipleVersions-v2.bpmn");
    deployResource("subscribeFormProcess.bpmn");
    deployResource("startedByFormProcessWithoutPublic.bpmn");
    deployResource("travelSearchProcess.bpmn");
    deployResource("travelSearchProcess_v2.bpmn");
    deployResource("requestAnnualLeave.bpmn");
    deployResource("two_processes.bpmn");
  }

  private void deployResource(final String classpathResource) {
    deployResourceWithoutAuthentication(classpathResource, DEFAULT_TENANT_IDENTIFIER);
    LOGGER.debug("Deployment of resource [{}] was performed", classpathResource);
  }

  public void deployResourceWithoutAuthentication(
      final String classpathResource, final String tenantId) {
    try (final InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream(classpathResource)) {
      if (resourceStream != null) {
        final byte[] bytes = StreamUtil.readInputStream(resourceStream);
        executeCamundaServiceAnonymously(
            (authentication) ->
                resourceServices.deployResources(
                    new DeployResourcesRequest(Map.of(classpathResource, bytes), tenantId),
                    authentication));
      } else {
        throw new FileNotFoundException(classpathResource);
      }
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy resource from classpath. %s", e.getMessage());
      throw new TasklistRuntimeException(exceptionMsg, e);
    }
  }

  public ProcessInstanceCreationRecord createProcessInstanceWithoutAuthentication(
      final String bpmnProcessId, final Map<String, Object> variables, final String tenantId) {
    return executeCamundaServiceAnonymously(
        (authentication) ->
            processInstanceServices.createProcessInstance(
                new ProcessInstanceCreateRequest(
                    -1L,
                    bpmnProcessId,
                    -1,
                    variables,
                    tenantId,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    Set.of(),
                    null),
                authentication));
  }

  private <T> T executeCamundaServiceAnonymously(
      final Function<CamundaAuthentication, CompletableFuture<T>> method) {
    return executeCamundaService(
        method, authenticationProvider.getAnonymousCamundaAuthentication());
  }

  private <T> T executeCamundaService(
      final Function<CamundaAuthentication, CompletableFuture<T>> method,
      final CamundaAuthentication authentication) {
    try {
      return method.apply(authentication).join();
    } catch (final Exception e) {
      throw e;
    }
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown DataGenerator");
    shutdown = true;
    if (executor != null && !executor.isShutdown()) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          executor.shutdownNow();
        }
      } catch (final InterruptedException e) {
        executor.shutdownNow();
      }
    }
  }
}
