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
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel;
import io.camunda.db.rdbms.write.domain.ProcessInstanceDbModel.ProcessInstanceDbModelBuilder;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;

/**
 * Reader that reads process instances from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 2).
 */
public final class ProcessInstanceReader {

  private static final String INDEX_NAME = "operate-list-view-8.3.0_alias";

  private ProcessInstanceReader() {}

  /**
   * Reads all process instances from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of ProcessInstanceForListViewEntity objects from ES
   */
  public static List<ProcessInstanceForListViewEntity> readAllProcessInstancesFromEs(
      final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(
            esClient, searchRequestBuilder, ProcessInstanceForListViewEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a ProcessInstanceForListViewEntity (ES model) to a ProcessInstanceDbModel (RDBMS
   * model).
   *
   * @param entity the ProcessInstanceForListViewEntity from Elasticsearch
   * @return the corresponding ProcessInstanceDbModel for RDBMS
   */
  public static ProcessInstanceDbModel toRdbmsModel(final ProcessInstanceForListViewEntity entity) {
    return new ProcessInstanceDbModelBuilder()
        .processInstanceKey(entity.getKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .state(mapState(entity.getState()))
        .startDate(entity.getStartDate())
        .endDate(entity.getEndDate())
        .tenantId(entity.getTenantId())
        .parentProcessInstanceKey(entity.getParentProcessInstanceKey())
        .parentElementInstanceKey(entity.getParentFlowNodeInstanceKey())
        .numIncidents(entity.isIncident() ? 1 : 0)
        .version(entity.getProcessVersion() != null ? entity.getProcessVersion() : 0)
        .partitionId(entity.getPartitionId())
        .treePath(entity.getTreePath())
        .tags(entity.getTags())
        .build();
  }

  private static ProcessInstanceState mapState(
      final io.camunda.webapps.schema.entities.listview.ProcessInstanceState esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState) {
      case ACTIVE -> ProcessInstanceState.ACTIVE;
      case COMPLETED -> ProcessInstanceState.COMPLETED;
      case CANCELED -> ProcessInstanceState.CANCELED;
    };
  }

  /**
   * Reads all process instances from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of ProcessInstanceDbModel objects ready for RDBMS insertion
   */
  public static List<ProcessInstanceDbModel> readProcessInstances(
      final ElasticsearchClient esClient) {
    return readAllProcessInstancesFromEs(esClient).stream()
        .map(ProcessInstanceReader::toRdbmsModel)
        .toList();
  }
}
