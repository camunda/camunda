/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.qa.backup.BackupRestoreTest.BACKUP_ID;
import static io.camunda.tasklist.qa.backup.BackupRestoreTest.INDEX_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.rest.StatefulRestTemplate;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
@Configuration
@EnableResilientMethods
public class TasklistAPICaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistAPICaller.class);
  private static final String COMPLETE_TASK_MUTATION_PATTERN =
      "mutation {completeTask(taskId: \"%s\", variables: [%s]){id name assignee taskState completionTime}}";

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate statefulRestTemplate;
  private StatefulRestTemplate mgmtRestTemplate;

  public List<TaskSearchResponse> getAllTasks() {
    final URI url = statefulRestTemplate.getURL("/v1/tasks/search");
    return Arrays.asList(
        statefulRestTemplate
            .postForEntity(
                url, new TaskSearchRequest().setPageSize(10_000), TaskSearchResponse[].class)
            .getBody());
  }

  public List<TaskSearchResponse> getTasks(final String taskBpmnId) {
    final URI url = statefulRestTemplate.getURL("/v1/tasks/search");
    return Arrays.asList(
        statefulRestTemplate
            .postForEntity(
                url,
                new TaskSearchRequest().setTaskDefinitionId(taskBpmnId),
                TaskSearchResponse[].class)
            .getBody());
  }

  public void completeTask(final String id, final VariableInputDTO... variables) {
    final URI url = statefulRestTemplate.getURL("/v1/tasks/%s/complete".formatted(id));
    statefulRestTemplate.patchForObject(
        url, new TaskCompleteRequest().setVariables(Arrays.asList(variables)), TaskResponse.class);
  }

  public StatefulRestTemplate createTasklistRestClient(final TestContext testContext) {
    final RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();
    final TestRestTemplate testRestTemplate = new TestRestTemplate(restTemplateBuilder);
    final Field restTemplateField;
    try {
      statefulRestTemplate =
          statefulRestTemplateFactory.apply(
              testContext.getExternalTasklistHost(), testContext.getExternalTasklistPort());
      mgmtRestTemplate =
          statefulRestTemplateFactory.apply(
              testContext.getExternalTasklistHost(), testContext.getExternalTasklistMgmtPort());
      restTemplateField = testRestTemplate.getClass().getDeclaredField("restTemplate");
      restTemplateField.setAccessible(true);
      restTemplateField.set(testRestTemplate, statefulRestTemplate);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    return statefulRestTemplate;
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

  @Retryable(maxRetries = 10, delay = 2000, includes = TasklistRuntimeException.class)
  public void checkIndicesAreDeleted(final ElasticsearchClient esClient) throws IOException {
    final int count = esClient.indices().get(gir -> gir.index(INDEX_PREFIX + "*")).result().size();
    if (count > 0) {
      LOGGER.warn("ElasticSearch indices are not yet removed.");
      throw new TasklistRuntimeException("Indices are not yet deleted.");
    }
  }

  @Retryable(maxRetries = 10, delay = 2000, includes = TasklistRuntimeException.class)
  public void checkIndicesAreDeleted(final OpenSearchClient osClient) throws IOException {
    final int count = osClient.indices().get(gir -> gir.index(INDEX_PREFIX + "*")).result().size();
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
        .featuresToEnable(JsonParser.Feature.ALLOW_COMMENTS)
        .build();
  }

  @Retryable(
      maxRetries = 100,
      delay = 10,
      includes = {AssertionError.class, HttpClientErrorException.NotFound.class})
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
