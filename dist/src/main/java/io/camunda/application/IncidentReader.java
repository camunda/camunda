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
import io.camunda.db.rdbms.write.domain.IncidentDbModel;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import java.util.List;

/**
 * Reader that reads incidents from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 5).
 */
public final class IncidentReader {

  private static final String INDEX_NAME = "operate-incident-8.3.1_";

  private IncidentReader() {}

  /**
   * Reads all incidents from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of IncidentEntity objects from ES
   */
  public static List<IncidentEntity> readAllIncidentsFromEs(final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, IncidentEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts an IncidentEntity (ES model) to an IncidentDbModel (RDBMS model).
   *
   * @param entity the IncidentEntity from Elasticsearch
   * @return the corresponding IncidentDbModel for RDBMS
   */
  public static IncidentDbModel toRdbmsModel(final IncidentEntity entity) {
    return new IncidentDbModel.Builder()
        .incidentKey(entity.getKey())
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .processInstanceKey(entity.getProcessInstanceKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getProcessInstanceKey())
        .flowNodeInstanceKey(entity.getFlowNodeInstanceKey())
        .flowNodeId(entity.getFlowNodeId())
        .jobKey(entity.getJobKey())
        .errorType(mapErrorType(entity.getErrorType()))
        .errorMessage(entity.getErrorMessage())
        .errorMessageHash(
            entity.getErrorMessage() != null ? entity.getErrorMessage().hashCode() : null)
        .creationDate(entity.getCreationTime())
        .state(mapState(entity.getState()))
        .treePath(entity.getTreePath())
        .tenantId(entity.getTenantId())
        .partitionId(entity.getPartitionId())
        .build();
  }

  private static IncidentState mapState(
      final io.camunda.webapps.schema.entities.incident.IncidentState esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState) {
      case ACTIVE -> IncidentState.ACTIVE;
      case MIGRATED -> IncidentState.MIGRATED;
      case RESOLVED -> IncidentState.RESOLVED;
      case PENDING -> IncidentState.PENDING;
    };
  }

  private static ErrorType mapErrorType(
      final io.camunda.webapps.schema.entities.incident.ErrorType esErrorType) {
    if (esErrorType == null) {
      return null;
    }
    return switch (esErrorType) {
      case UNSPECIFIED -> ErrorType.UNSPECIFIED;
      case UNKNOWN -> ErrorType.UNKNOWN;
      case IO_MAPPING_ERROR -> ErrorType.IO_MAPPING_ERROR;
      case JOB_NO_RETRIES -> ErrorType.JOB_NO_RETRIES;
      case EXECUTION_LISTENER_NO_RETRIES -> ErrorType.EXECUTION_LISTENER_NO_RETRIES;
      case TASK_LISTENER_NO_RETRIES -> ErrorType.TASK_LISTENER_NO_RETRIES;
      case AD_HOC_SUB_PROCESS_NO_RETRIES -> ErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES;
      case CONDITION_ERROR -> ErrorType.CONDITION_ERROR;
      case EXTRACT_VALUE_ERROR -> ErrorType.EXTRACT_VALUE_ERROR;
      case CALLED_ELEMENT_ERROR -> ErrorType.CALLED_ELEMENT_ERROR;
      case UNHANDLED_ERROR_EVENT -> ErrorType.UNHANDLED_ERROR_EVENT;
      case MESSAGE_SIZE_EXCEEDED -> ErrorType.MESSAGE_SIZE_EXCEEDED;
      case CALLED_DECISION_ERROR -> ErrorType.CALLED_DECISION_ERROR;
      case DECISION_EVALUATION_ERROR -> ErrorType.DECISION_EVALUATION_ERROR;
      case FORM_NOT_FOUND -> ErrorType.FORM_NOT_FOUND;
      case RESOURCE_NOT_FOUND -> ErrorType.RESOURCE_NOT_FOUND;
    };
  }

  /**
   * Reads all incidents from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of IncidentDbModel objects ready for RDBMS insertion
   */
  public static List<IncidentDbModel> readIncidents(final ElasticsearchClient esClient) {
    return readAllIncidentsFromEs(esClient).stream().map(IncidentReader::toRdbmsModel).toList();
  }
}
