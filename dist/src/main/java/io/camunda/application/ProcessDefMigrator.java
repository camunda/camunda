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
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel;
import io.camunda.db.rdbms.write.domain.ProcessDefinitionDbModel.ProcessDefinitionDbModelBuilder;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.List;

/**
 * Migrator that reads process definitions from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling.
 */
public final class ProcessDefMigrator {

  private static final String INDEX_NAME = "operate-process-8.3.0_alias";

  private ProcessDefMigrator() {}

  /**
   * Reads all process definitions from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of ProcessEntity objects from ES
   */
  private static List<ProcessEntity> readAllProcessEntitiesFromEs(
      final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder().index(INDEX_NAME).query(ElasticsearchUtil.matchAllQuery());

    return ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, ProcessEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a ProcessEntity (ES model) to a ProcessDefinitionDbModel (RDBMS model).
   *
   * @param entity the ProcessEntity from Elasticsearch
   * @return the corresponding ProcessDefinitionDbModel for RDBMS
   */
  public static ProcessDefinitionDbModel toRdbmsModel(final ProcessEntity entity) {
    return new ProcessDefinitionDbModelBuilder()
        .processDefinitionKey(entity.getKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .resourceName(entity.getResourceName())
        .name(entity.getName())
        .tenantId(entity.getTenantId())
        .versionTag(entity.getVersionTag())
        .version(entity.getVersion())
        .bpmnXml(entity.getBpmnXml())
        .formId(entity.getFormId())
        .build();
  }

  /**
   * Reads all process definitions from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of ProcessDefinitionDbModel objects ready for RDBMS insertion
   */
  public static List<ProcessDefinitionDbModel> migrateProcessDefinitions(
      final ElasticsearchClient esClient) {
    return readAllProcessEntitiesFromEs(esClient).stream()
        .map(ProcessDefMigrator::toRdbmsModel)
        .toList();
  }
}
