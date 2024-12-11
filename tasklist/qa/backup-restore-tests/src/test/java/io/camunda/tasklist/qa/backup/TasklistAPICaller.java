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
import static io.camunda.webapps.backup.BackupStateDto.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.rest.StatefulRestTemplate;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskCompleteRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import java.io.IOException;
import java.lang.reflect.Field;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ResourceLoader resourceLoader;

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate statefulRestTemplate;
  private StatefulRestTemplate mgmtRestTemplate;

  public List<TaskSearchResponse> getAllTasks() throws IOException {
    final TaskSearchRequest searchRequest = new TaskSearchRequest();
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    final HttpEntity<TaskSearchRequest> requestEntity = new HttpEntity<>(searchRequest, headers);

    final ResponseEntity<List<TaskSearchResponse>> response =
        statefulRestTemplate.exchange(
            statefulRestTemplate.getURL("v1/tasks/search"),
            HttpMethod.POST,
            requestEntity,
            new ParameterizedTypeReference<List<TaskSearchResponse>>() {});

    return response.getBody();
  }

  public List<TaskSearchResponse> getTasks(final String taskBpmnId) throws IOException {
    final TaskSearchRequest searchRequest = new TaskSearchRequest();
    searchRequest.setTaskDefinitionId(taskBpmnId);
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    final HttpEntity<TaskSearchRequest> requestEntity = new HttpEntity<>(searchRequest, headers);

    final ResponseEntity<List<TaskSearchResponse>> response =
        statefulRestTemplate.exchange(
            statefulRestTemplate.getURL("v1/tasks/search"),
            HttpMethod.POST,
            requestEntity,
            new ParameterizedTypeReference<List<TaskSearchResponse>>() {});

    return response.getBody();
  }

  public void completeTask(final String id, final List<VariableInputDTO> variables) {
    final TaskCompleteRequest request = new TaskCompleteRequest();
    request.setVariables(variables);

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    final HttpEntity<TaskCompleteRequest> requestEntity = new HttpEntity<>(request, headers);

    final var response =
        statefulRestTemplate.exchange(
            statefulRestTemplate.getURL(String.format("v1/tasks/%s/complete", id)),
            HttpMethod.PATCH,
            requestEntity,
            Void.class);

    assertThat(response.getStatusCode().is2xxSuccessful());
  }

  public void createRestContext(final TestContext testContext) {
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
      value = {AssertionError.class, HttpClientErrorException.NotFound.class},
      maxAttempts = 10,
      backoff = @Backoff(delay = 2000))
  public void assertBackupState() {
    try {
      final GetBackupStateResponseDto backupState = getBackupState(BACKUP_ID);
      assertThat(backupState.getState()).isIn(IN_PROGRESS, COMPLETED);
      // to retry
      assertThat(backupState.getState()).isEqualTo(COMPLETED);
    } catch (final AssertionError | HttpClientErrorException.NotFound er) {
      LOGGER.warn("Error when checking backup state: " + er.getMessage());
      throw er;
    }
  }
}
