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
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel;
import io.camunda.db.rdbms.write.domain.FlowNodeInstanceDbModel.FlowNodeInstanceDbModelBuilder;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import java.util.List;

/**
 * Reader that reads flow node instances from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 3).
 */
public final class FlowNodeInstanceReader {

  private static final String INDEX_NAME = "operate-flownode-instance-8.3.1_alias";

  private FlowNodeInstanceReader() {}

  /**
   * Reads all flow node instances from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of FlowNodeInstanceEntity objects from ES
   */
  public static List<FlowNodeInstanceEntity> readAllFlowNodeInstancesFromEs(
      final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(
            esClient, searchRequestBuilder, FlowNodeInstanceEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a FlowNodeInstanceEntity (ES model) to a FlowNodeInstanceDbModel (RDBMS model).
   *
   * @param entity the FlowNodeInstanceEntity from Elasticsearch
   * @return the corresponding FlowNodeInstanceDbModel for RDBMS
   */
  public static FlowNodeInstanceDbModel toRdbmsModel(final FlowNodeInstanceEntity entity) {
    return new FlowNodeInstanceDbModelBuilder()
        .flowNodeInstanceKey(entity.getKey())
        .processInstanceKey(entity.getProcessInstanceKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getProcessInstanceKey())
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .flowNodeScopeKey(entity.getScopeKey())
        .startDate(entity.getStartDate())
        .endDate(entity.getEndDate())
        .flowNodeId(entity.getFlowNodeId())
        .flowNodeName(entity.getFlowNodeName())
        .treePath(entity.getTreePath())
        .type(mapType(entity.getType()))
        .state(mapState(entity.getState()))
        .incidentKey(entity.getIncidentKey())
        .hasIncident(entity.isIncident())
        .tenantId(entity.getTenantId())
        .partitionId(entity.getPartitionId())
        .build();
  }

  private static FlowNodeState mapState(
      final io.camunda.webapps.schema.entities.flownode.FlowNodeState esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState) {
      case ACTIVE -> FlowNodeState.ACTIVE;
      case COMPLETED -> FlowNodeState.COMPLETED;
      case TERMINATED -> FlowNodeState.TERMINATED;
    };
  }

  private static FlowNodeType mapType(
      final io.camunda.webapps.schema.entities.flownode.FlowNodeType esType) {
    if (esType == null) {
      return null;
    }
    return switch (esType) {
      case UNSPECIFIED -> FlowNodeType.UNSPECIFIED;
      case PROCESS -> FlowNodeType.PROCESS;
      case SUB_PROCESS -> FlowNodeType.SUB_PROCESS;
      case EVENT_SUB_PROCESS -> FlowNodeType.EVENT_SUB_PROCESS;
      case START_EVENT -> FlowNodeType.START_EVENT;
      case INTERMEDIATE_CATCH_EVENT -> FlowNodeType.INTERMEDIATE_CATCH_EVENT;
      case INTERMEDIATE_THROW_EVENT -> FlowNodeType.INTERMEDIATE_THROW_EVENT;
      case BOUNDARY_EVENT -> FlowNodeType.BOUNDARY_EVENT;
      case END_EVENT -> FlowNodeType.END_EVENT;
      case SERVICE_TASK -> FlowNodeType.SERVICE_TASK;
      case RECEIVE_TASK -> FlowNodeType.RECEIVE_TASK;
      case USER_TASK -> FlowNodeType.USER_TASK;
      case MANUAL_TASK -> FlowNodeType.MANUAL_TASK;
      case TASK -> FlowNodeType.TASK;
      case EXCLUSIVE_GATEWAY -> FlowNodeType.EXCLUSIVE_GATEWAY;
      case INCLUSIVE_GATEWAY -> FlowNodeType.INCLUSIVE_GATEWAY;
      case PARALLEL_GATEWAY -> FlowNodeType.PARALLEL_GATEWAY;
      case EVENT_BASED_GATEWAY -> FlowNodeType.EVENT_BASED_GATEWAY;
      case SEQUENCE_FLOW -> FlowNodeType.SEQUENCE_FLOW;
      case MULTI_INSTANCE_BODY -> FlowNodeType.MULTI_INSTANCE_BODY;
      case CALL_ACTIVITY -> FlowNodeType.CALL_ACTIVITY;
      case BUSINESS_RULE_TASK -> FlowNodeType.BUSINESS_RULE_TASK;
      case SCRIPT_TASK -> FlowNodeType.SCRIPT_TASK;
      case SEND_TASK -> FlowNodeType.SEND_TASK;
      case UNKNOWN -> FlowNodeType.UNKNOWN;
      default -> FlowNodeType.UNKNOWN;
    };
  }

  /**
   * Reads all flow node instances from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of FlowNodeInstanceDbModel objects ready for RDBMS insertion
   */
  public static List<FlowNodeInstanceDbModel> readFlowNodeInstances(
      final ElasticsearchClient esClient) {
    return readAllFlowNodeInstancesFromEs(esClient).stream()
        .map(FlowNodeInstanceReader::toRdbmsModel)
        .toList();
  }
}
