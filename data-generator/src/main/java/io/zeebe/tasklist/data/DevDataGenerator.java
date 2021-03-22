/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.data;

import static io.zeebe.tasklist.util.ThreadUtil.sleepFor;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ZeebeClient;
import io.zeebe.tasklist.entities.FormEntity;
import io.zeebe.tasklist.entities.UserEntity;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.schema.indices.FormIndex;
import io.zeebe.tasklist.schema.indices.UserIndex;
import io.zeebe.tasklist.schema.templates.TaskTemplate;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.util.ZeebeTestUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
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
        UserEntity.from(username, passwordEncoded, "USER")
            .setFirstname(firstname)
            .setLastname(lastname);
    try {
      final IndexRequest request =
          new IndexRequest(
                  userIndex.getFullQualifiedName(), ElasticsearchUtil.ES_INDEX_TYPE, user.getId())
              .source(userEntityToJSONString(user), XContentType.JSON);
      esClient.index(request, RequestOptions.DEFAULT);
    } catch (Exception t) {
      LOGGER.error("Could not create demo user with username {}", user.getUsername(), t);
    }
    LOGGER.info("Created demo user {} with password {}", username, password);
  }

  protected String userEntityToJSONString(UserEntity aUser) throws JsonProcessingException {
    return objectMapper.writeValueAsString(aUser);
  }

  private void createZeebeData() {
    deployWorkflows();
    startWorkflowInstances();
  }

  private void startWorkflowInstances() {
    final int instancesCount = random.nextInt(20) + 20;
    for (int i = 0; i < instancesCount; i++) {
      startOrderProcess();
      startFlightRegistrationProcess();
      startSimpleProcess();
    }
    // TODO remove this when import is ready
    mockTaskForms();
  }

  private void mockTaskForms() {
    try {
      final String formJSON =
          "{\n"
              + "  \"components\": [\n"
              + "    {\n"
              + "      \"key\": \"firstName\",\n"
              + "      \"label\": \"First name\",\n"
              + "      \"type\": \"textfield\",\n"
              + "      \"validate\": {\n"
              + "        \"required\": true\n"
              + "      }\n"
              + "    },\n"
              + "    {\n"
              + "      \"key\": \"lastName\",\n"
              + "      \"label\": \"Last name\",\n"
              + "      \"type\": \"textfield\",\n"
              + "      \"validate\": {\n"
              + "        \"required\": true\n"
              + "      }\n"
              + "    },\n"
              + "    {\n"
              + "      \"key\": \"passNo\",\n"
              + "      \"label\": \"Passport number\",\n"
              + "      \"type\": \"textfield\",\n"
              + "      \"validate\": {\n"
              + "        \"required\": true\n"
              + "      }\n"
              + "    },\n"
              + "    {\n"
              + "      \"key\": \"ticketNo\",\n"
              + "      \"label\": \"Ticket number\",\n"
              + "      \"type\": \"textfield\",\n"
              + "      \"validate\": {\n"
              + "        \"required\": true\n"
              + "      }\n"
              + "    },\n"
              + "    {\n"
              + "      \"key\": \"registered\",\n"
              + "      \"label\": \"Registered successfully\",\n"
              + "      \"type\": \"checkbox\"\n"
              + "    }\n"
              + "  ],\n"
              + "  \"type\": \"default\"\n"
              + "}";
      final FormEntity formEntity = new FormEntity("workflowId", "formKey", formJSON);
      final IndexRequest indexRequest =
          new IndexRequest(
                  formIndex.getFullQualifiedName(),
                  ElasticsearchUtil.ES_INDEX_TYPE,
                  formEntity.getId())
              .source(objectMapper.writeValueAsString(formEntity), XContentType.JSON);
      try {
        esClient.index(indexRequest, RequestOptions.DEFAULT);
      } catch (IOException e) {
        LOGGER.error(e.getMessage(), e);
      }
      sleepFor(10000L);
      // update registerPassenger tasks with formKey
      final UpdateByQueryRequest updateRequest =
          new UpdateByQueryRequest(taskTemplate.getFullQualifiedName())
              .setQuery(termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, "registerPassenger"))
              .setScript(
                  new Script(
                      ScriptType.INLINE,
                      "painless",
                      "ctx._source.formId = \"workflowId_formKey\";",
                      Collections.emptyMap()));
      esClient.updateByQueryAsync(
          updateRequest,
          RequestOptions.DEFAULT,
          new ActionListener<>() {
            @Override
            public void onResponse(final BulkByScrollResponse bulkByScrollResponse) {
              LOGGER.debug("tasks updated");
            }

            @Override
            public void onFailure(final Exception e) {
              LOGGER.error(e.getMessage(), e);
            }
          });
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  private void startSimpleProcess() {
    ZeebeTestUtil.startWorkflowInstance(zeebeClient, "simpleProcess", null);
  }

  private void startOrderProcess() {
    final float price1 = Math.round(random.nextFloat() * 100000) / 100;
    final float price2 = Math.round(random.nextFloat() * 10000) / 100;
    ZeebeTestUtil.startWorkflowInstance(
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
    ZeebeTestUtil.startWorkflowInstance(
        zeebeClient,
        "flightRegistration",
        "{\n"
            + "  \"firstName\": \""
            + NameGenerator.getRandomFirstName()
            + "\",\n"
            + "  \"lastName\": \""
            + NameGenerator.getRandomLastName()
            + "\",\n"
            + "  \"passNo\": \"PS"
            + (random.nextInt(1000000) + (random.nextInt(9) + 1) * 1000000)
            + "\",\n"
            + "  \"ticketNo\": \""
            + random.nextInt(1000)
            + "\"\n"
            + "}");
  }

  private void deployWorkflows() {
    ZeebeTestUtil.deployWorkflow(zeebeClient, "orderProcess.bpmn");
    ZeebeTestUtil.deployWorkflow(zeebeClient, "registerPassenger.bpmn");
    ZeebeTestUtil.deployWorkflow(zeebeClient, "simpleProcess.bpmn");
  }

  public boolean shouldCreateData() {
    try {
      final GetIndexRequest request =
          new GetIndexRequest(tasklistProperties.getZeebeElasticsearch().getPrefix() + "*");
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
