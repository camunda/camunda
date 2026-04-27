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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.util.rest.StatefulRestTemplate;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.TakeBackupResponseDto;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
@Configuration
@EnableResilientMethods
public class TasklistAPICaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(TasklistAPICaller.class);

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;

  private StatefulRestTemplate statefulRestTemplate;
  private StatefulRestTemplate mgmtRestTemplate;
  private CamundaClient client;

  public SearchResponse<UserTask> getAllTasks() {
    return client.newUserTaskSearchRequest().send().join();
  }

  public SearchResponse<UserTask> getTasks(final String taskBpmnId) {
    return client.newUserTaskSearchRequest().filter(f -> f.bpmnProcessId(taskBpmnId)).send().join();
  }

  public void completeTask(final Long usetTaskKey, final VariableInputDTO... variables) {
    client.newCompleteUserTaskCommand(usetTaskKey).variables(List.of(variables)).send().join();
  }

  public void saveDraftTaskVariables(
      final Long userTaskElementInstanceKey, final Map<String, String> draftVariables) {
    // this feature is not supported in the V2 API, local variables are used
    // as a replacement:
    // https://docs.camunda.io/docs/next/apis-tools/migration-manuals/migrate-to-camunda-api/#save-task-draft-variables
    client
        .newSetVariablesCommand(userTaskElementInstanceKey)
        .variables(draftVariables)
        .local(true)
        .send()
        .join();
  }

  public void createClients(final BackupRestoreTestContext testContext) {
    client = testContext.getCamundaClient();
    mgmtRestTemplate =
        statefulRestTemplateFactory.apply(
            testContext.getExternalTasklistHost(), testContext.getExternalTasklistMgmtPort());
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    client = camundaClient;
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
    final ObjectMapper mapper = new ObjectMapper();
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    mapper.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    mapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
    return mapper;
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
