/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.data;

import static java.util.Arrays.asList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.PayloadUtil;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev-data")
public class DevDataGenerator implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGenerator.class);

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private ZeebeClient zeebeClient;

  @Autowired private FormIndex formIndex;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private PayloadUtil payloadUtil;

  private Random random = new Random();

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Autowired private ObjectMapper objectMapper;

  @Autowired private UserIndex userIndex;

  private boolean shutdown = false;

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
              } catch (Exception ex) {
                LOGGER.error("Demo data was not generated, will retry", ex);
              }
            }
          });
    }
  }

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

  private void createUser(String username, String firstname, String lastname) {
    final String password = username;
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity user =
        UserEntity.from(username, passwordEncoded, List.of("USER"))
            .setDisplayName(firstname + " " + lastname)
            .setRoles(asList("OWNER"));
    try {
      final IndexRequest request =
          new IndexRequest(userIndex.getFullQualifiedName())
              .id(user.getId())
              .source(userEntityToJSONString(user), XContentType.JSON);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Exception t) {
      LOGGER.error("Could not create demo user with user id {}", user.getUserId(), t);
    }
    LOGGER.info("Created demo user {} with password {}", username, password);
  }

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
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
    ZeebeTestUtil.startProcessInstance(zeebeClient, "simpleProcess", payload);
  }

  private void startBigFormProcess() {
    ZeebeTestUtil.startProcessInstance(zeebeClient, "bigFormProcess", null);
  }

  private void startCarForRentProcess() {
    ZeebeTestUtil.startProcessInstance(zeebeClient, "registerCarForRent", null);
  }

  private void startTwoUserTasks() {
    ZeebeTestUtil.startProcessInstance(zeebeClient, "twoUserTasks", null);
  }

  private void startOrderProcess() {
    final float price1 = Math.round(random.nextFloat() * 100000) / 100;
    final float price2 = Math.round(random.nextFloat() * 10000) / 100;
    ZeebeTestUtil.startProcessInstance(
        zeebeClient,
        "orderProcess",
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
            + "}");
  }

  private void startFlightRegistrationProcess() {
    final String payload =
        "{\"candidateGroups\": [\"group1\", \"group2\"]," + "\"assignee\": \"demo\"}";
    ZeebeTestUtil.startProcessInstance(zeebeClient, "flightRegistration", payload);
  }

  private void deployProcesses() {
    ZeebeTestUtil.deployProcess(zeebeClient, "orderProcess.bpmn");
    ZeebeTestUtil.deployProcess(zeebeClient, "registerPassenger.bpmn");
    ZeebeTestUtil.deployProcess(zeebeClient, "simpleProcess.bpmn");
    ZeebeTestUtil.deployProcess(zeebeClient, "bigFormProcess.bpmn");
    ZeebeTestUtil.deployProcess(zeebeClient, "registerCarForRent.bpmn");
    ZeebeTestUtil.deployProcess(zeebeClient, "twoUserTasks.bpmn");
  }

  public boolean shouldCreateData() {
    try {
      final GetIndexRequest request =
          new GetIndexRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
      request.indicesOptions(IndicesOptions.fromOptions(true, false, true, false));
      final boolean exists = zeebeEsClient.indices().exists(request, RequestOptions.DEFAULT);
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    } catch (IOException io) {
      LOGGER.debug(
          "Error occurred while checking existance of data in Zeebe: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
    return true;
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
      } catch (InterruptedException e) {
        executor.shutdownNow();
      }
    }
  }
}
