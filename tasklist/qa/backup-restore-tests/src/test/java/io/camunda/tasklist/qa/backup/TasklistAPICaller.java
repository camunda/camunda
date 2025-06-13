/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.qa.backup.BackupRestoreTest.BACKUP_ID;
import static io.camunda.tasklist.qa.backup.BackupRestoreTest.ZEEBE_INDEX_PREFIX;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.tasklist.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphql.spring.boot.test.GraphQLResponse;
import com.graphql.spring.boot.test.GraphQLTestTemplate;
import com.jayway.jsonpath.PathNotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.rest.StatefulRestTemplate;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
@Configuration
@EnableRetry
public class TasklistAPICaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistAPICaller.class);
  private static final String COMPLETE_TASK_MUTATION_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: [%s]){id name assignee taskState completionTime}}";
  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ResourceLoader resourceLoader;

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private GraphQLTestTemplate graphQLTestTemplate;
  private StatefulRestTemplate statefulRestTemplate;
  private StatefulRestTemplate mgmtRestTemplate;

  public GraphQLResponse getAllTasks() throws IOException {
    final GraphQLResponse graphQLResponse =
        graphQLTestTemplate.postForResource("get-all-tasks.graphql");
    try {
      final Object errors = graphQLResponse.getRaw("$.errors");
      if (errors != null && ((List) errors).size() > 0) {
        throw new TasklistRuntimeException("Error occurred when getting the tasks: " + errors);
      }
    } catch (final PathNotFoundException ex) {
      // ignore
    }
    return graphQLResponse;
  }

  public List<TaskDTO> getTasks(final String taskBpmnId) throws IOException {
    final ObjectNode query = objectMapper.createObjectNode();
    query.putObject("query").put("taskDefinitionId", taskBpmnId);

    final GraphQLResponse graphQLResponse =
        graphQLTestTemplate.perform("get-tasks-by-query.graphql", query, new ArrayList<>());
    return graphQLResponse.getList("$.data.tasks", TaskDTO.class);
  }

  public void completeTask(final String id, final String variablesJson) {
    final GraphQLResponse response =
        graphQLTestTemplate.postMultipart(
            String.format(COMPLETE_TASK_MUTATION_PATTERN, id, variablesJson), "{}");
    assertThat(response.isOk()).isTrue();
  }

  public List<TaskDTO> getTasksByPath(final GraphQLResponse graphQLResponse, final String path) {
    return graphQLResponse.getList(path, TaskDTO.class);
  }

  public GraphQLTestTemplate createGraphQLTestTemplate(final TestContext testContext) {
    final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
    final TestRestTemplate testRestTemplate = new TestRestTemplate(restTemplateBuilder);
    final Field restTemplateField;
    try {
      statefulRestTemplate =
          statefulRestTemplateFactory.apply(
              testContext.getExternalTasklistHost(), testContext.getExternalTasklistPort());
      statefulRestTemplate.loginWhenNeeded(USERNAME, PASSWORD);
      mgmtRestTemplate =
          statefulRestTemplateFactory.apply(
              testContext.getExternalTasklistHost(), testContext.getExternalTasklistMgmtPort());
      restTemplateField = testRestTemplate.getClass().getDeclaredField("restTemplate");
      restTemplateField.setAccessible(true);
      restTemplateField.set(testRestTemplate, statefulRestTemplate);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    graphQLTestTemplate =
        new GraphQLTestTemplate(
            resourceLoader,
            testRestTemplate,
            String.format(
                "http://%s:%s/graphql",
                testContext.getExternalTasklistHost(), testContext.getExternalTasklistPort()),
            objectMapper);
    return graphQLTestTemplate;
  }

  public void saveDraftTaskVariables(
      final String taskId, final SaveVariablesRequest saveVariablesRequest) {
    final var response =
        statefulRestTemplate.postForEntity(
            statefulRestTemplate.getURL(String.format("v1/tasks/%s/variables", taskId)),
            saveVariablesRequest,
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  public TakeBackupResponseDto backup(final Long backupId) {
    final TakeBackupRequestDto takeBackupRequest = new TakeBackupRequestDto().setBackupId(backupId);
    return mgmtRestTemplate.postForObject(
        mgmtRestTemplate.getURL("actuator/backups"),
        takeBackupRequest,
        TakeBackupResponseDto.class);
  }

  public GetBackupStateResponseDto getBackupState(final Long backupId) {
    return mgmtRestTemplate.getForObject(
        mgmtRestTemplate.getURL("actuator/backups/" + backupId), GetBackupStateResponseDto.class);
  }

  @Retryable(
      retryFor = {TasklistRuntimeException.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000))
  public void checkIndicesAreDeleted(final RestHighLevelClient esClient) throws IOException {
    final int count =
        esClient
            .indices()
            .get(new GetIndexRequest("tasklist*", ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT)
            .getIndices()
            .length;
    if (count > 0) {
      LOGGER.warn("ElasticSearch indices are not yet removed.");
      throw new TasklistRuntimeException("Indices are not yet deleted.");
    }
  }

  @Retryable(
      retryFor = {TasklistRuntimeException.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000))
  public void checkIndicesAreDeleted(final OpenSearchClient osClient) throws IOException {
    final int count =
        osClient
            .indices()
            .get(gir -> gir.index("tasklist*", ZEEBE_INDEX_PREFIX + "*"))
            .result()
            .size();
    if (count > 0) {
      LOGGER.warn("OpenSearch indices are not yet removed.");
      throw new TasklistRuntimeException("Indices are not yet deleted.");
    }
  }

  @Bean
  public ObjectMapper objectMapper() {
    return Jackson2ObjectMapperBuilder.json()
        .featuresToDisable(
            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS, SerializationFeature.INDENT_OUTPUT)
        .build();
  }

  @Retryable(
      retryFor = {AssertionError.class, HttpClientErrorException.NotFound.class},
      maxAttempts = 100,
      backoff = @Backoff(delay = 10)) // short delay to verify that INCOMPLETE state is not returned
  public void assertBackupState() {
    try {
      final var backupState = getBackupState(BACKUP_ID).getState();
      switch (backupState) {
        case COMPLETED:
          LOGGER.info("Backup completed successfully.");
          break;
        case IN_PROGRESS:
          LOGGER.info("Backup is still in progress, retrying...");
          throw new AssertionError("Backup is still in progress"); // Triggers retry
        default:
          LOGGER.error("Unhealthy backup state: {}", backupState);
          throw new IllegalStateException("Unhealthy backup state: " + backupState);
      }
    } catch (final HttpClientErrorException.NotFound er) {
      LOGGER.warn("Error when checking backup state: {}", er.getMessage());
      throw er;
    }
  }
}
