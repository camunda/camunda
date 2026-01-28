/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.db.rdbms.write.domain.JobDbModel;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.search.entities.JobEntity.JobKind;
import io.camunda.search.entities.JobEntity.JobState;
import io.camunda.search.entities.JobEntity.ListenerEventType;
import io.camunda.webapps.schema.entities.JobEntity;
import java.util.List;

/**
 * Reader that reads jobs from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 3).
 */
public final class JobReader {

  private static final String INDEX_NAME = "operate-job-8.3.0_alias";

  private JobReader() {}

  /**
   * Reads all jobs from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of JobEntity objects from ES
   */
  public static List<JobEntity> readAllJobsFromEs(final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, JobEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a JobEntity (ES model) to a JobDbModel (RDBMS model).
   *
   * @param entity the JobEntity from Elasticsearch
   * @return the corresponding JobDbModel for RDBMS
   */
  public static JobDbModel toRdbmsModel(final JobEntity entity) {
    return new JobDbModel.Builder()
        .jobKey(entity.getKey())
        .type(entity.getType())
        .worker(entity.getWorker())
        .state(mapState(entity.getState()))
        .kind(mapKind(entity.getJobKind()))
        .listenerEventType(mapListenerEventType(entity.getListenerEventType()))
        .retries(entity.getRetries())
        .isDenied(entity.isDenied())
        .deniedReason(entity.getDeniedReason())
        .hasFailedWithRetriesLeft(entity.isJobFailedWithRetriesLeft())
        .errorCode(entity.getErrorCode())
        .errorMessage(entity.getErrorMessage())
        .customHeaders(entity.getCustomHeaders())
        .deadline(entity.getDeadline())
        .endTime(entity.getEndTime())
        .processDefinitionId(entity.getBpmnProcessId())
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .processInstanceKey(entity.getProcessInstanceKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getProcessInstanceKey())
        .elementId(entity.getFlowNodeId())
        .elementInstanceKey(entity.getFlowNodeInstanceId())
        .tenantId(entity.getTenantId())
        .partitionId(entity.getPartitionId())
        .creationTime(entity.getCreationTime())
        .lastUpdateTime(entity.getLastUpdateTime())
        .build();
  }

  private static JobState mapState(final String esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState.toUpperCase()) {
      case "CREATED" -> JobState.CREATED;
      case "COMPLETED" -> JobState.COMPLETED;
      case "FAILED" -> JobState.FAILED;
      case "CANCELED" -> JobState.CANCELED;
      case "ERROR_THROWN" -> JobState.ERROR_THROWN;
      case "TIMED_OUT" -> JobState.TIMED_OUT;
      case "MIGRATED" -> JobState.MIGRATED;
      case "RETRIES_UPDATED" -> JobState.RETRIES_UPDATED;
      default -> null;
    };
  }

  private static JobKind mapKind(final String esKind) {
    if (esKind == null) {
      return null;
    }
    return switch (esKind.toUpperCase()) {
      case "BPMN_ELEMENT" -> JobKind.BPMN_ELEMENT;
      case "EXECUTION_LISTENER" -> JobKind.EXECUTION_LISTENER;
      case "TASK_LISTENER" -> JobKind.TASK_LISTENER;
      default -> null;
    };
  }

  private static ListenerEventType mapListenerEventType(final String esListenerEventType) {
    if (esListenerEventType == null) {
      return null;
    }
    return switch (esListenerEventType.toUpperCase()) {
      case "START" -> ListenerEventType.START;
      case "END" -> ListenerEventType.END;
      case "COMPLETING" -> ListenerEventType.COMPLETING;
      case "ASSIGNING" -> ListenerEventType.ASSIGNING;
      case "UPDATING" -> ListenerEventType.UPDATING;
      case "CREATING" -> ListenerEventType.CREATING;
      case "CANCELING" -> ListenerEventType.CANCELING;
      case "UNSPECIFIED" -> ListenerEventType.UNSPECIFIED;
      default -> null;
    };
  }

  /**
   * Reads all jobs from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of JobDbModel objects ready for RDBMS insertion
   */
  public static List<JobDbModel> readJobs(final ElasticsearchClient esClient) {
    return readAllJobsFromEs(esClient).stream().map(JobReader::toRdbmsModel).toList();
  }
}
