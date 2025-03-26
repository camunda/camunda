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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.PayloadUtil;
import io.camunda.tasklist.zeebe.TasklistServicesAdapter;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DependsOn("tasklistSchemaStartup")
public abstract class DevDataGeneratorAbstract implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorAbstract.class);

  @Autowired protected TasklistProperties tasklistProperties;
  @Autowired protected UserIndex userIndex;
  protected PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Autowired private TasklistServicesAdapter tasklistServicesAdapter;

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private PayloadUtil payloadUtil;

  private final Random random = new Random();

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

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
                createDemoUsers();
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

  @Override
  public void createDemoUsers() {
    createUser("john", "John", "Doe");
    createUser("jane", "Jane", "Doe");
    createUser("joe", "Average", "Joe");
    for (int i = 0; i < 5; i++) {
      final String firstname = NameGenerator.getRandomFirstName();
      final String lastname = NameGenerator.getRandomLastName();
      createUser(firstname + "." + lastname, firstname, lastname);
    }
  }

  protected String userEntityToJSONString(final UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
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
            tasklistServicesAdapter.createProcessInstanceWithoutAuthentication(
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
    tasklistServicesAdapter.deployResourceWithoutAuthentication(
        classpathResource, DEFAULT_TENANT_IDENTIFIER);
    LOGGER.debug("Deployment of resource [{}] was performed", classpathResource);
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
