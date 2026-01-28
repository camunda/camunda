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
import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.VariableDbModelBuilder;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.entities.VariableEntity;
import java.util.List;

/**
 * Reader that reads variables from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 3).
 */
public final class VariableReader {

  private static final String INDEX_NAME = "operate-variable-8.3.0_alias";

  private VariableReader() {}

  /**
   * Reads all variables from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of VariableEntity objects from ES
   */
  public static List<VariableEntity> readAllVariablesFromEs(final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, VariableEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a VariableEntity (ES model) to a VariableDbModel (RDBMS model).
   *
   * @param entity the VariableEntity from Elasticsearch
   * @return the corresponding VariableDbModel for RDBMS
   */
  public static VariableDbModel toRdbmsModel(final VariableEntity entity) {
    return new VariableDbModelBuilder()
        .variableKey(entity.getKey())
        .name(entity.getName())
        .value(entity.getFullValue() != null ? entity.getFullValue() : entity.getValue())
        .scopeKey(entity.getScopeKey())
        .processInstanceKey(entity.getProcessInstanceKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getProcessInstanceKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .tenantId(entity.getTenantId())
        .partitionId(entity.getPartitionId())
        .build();
  }

  /**
   * Reads all variables from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of VariableDbModel objects ready for RDBMS insertion
   */
  public static List<VariableDbModel> readVariables(final ElasticsearchClient esClient) {
    return readAllVariablesFromEs(esClient).stream().map(VariableReader::toRdbmsModel).toList();
  }
}
