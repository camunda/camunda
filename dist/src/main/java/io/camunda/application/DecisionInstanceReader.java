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
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceInputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceOutputEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import java.util.Collections;
import java.util.List;

/**
 * Reader that reads decision instances from Elasticsearch and converts them to RDBMS models.
 *
 * <p>This is part of the ES to RDBMS migration tooling (Tier 4).
 */
public final class DecisionInstanceReader {

  private static final String INDEX_NAME = "operate-decision-instance-8.3.0_";

  private DecisionInstanceReader() {}

  /**
   * Reads all decision instances from Elasticsearch.
   *
   * @param esClient the Elasticsearch client
   * @return list of DecisionInstanceEntity objects from ES
   */
  public static List<DecisionInstanceEntity> readAllDecisionInstancesFromEs(
      final ElasticsearchClient esClient) {
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(INDEX_NAME)
            .query(ElasticsearchUtil.matchAllQuery())
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    return ElasticsearchUtil.scrollAllStream(
            esClient, searchRequestBuilder, DecisionInstanceEntity.class)
        .flatMap(response -> response.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  /**
   * Converts a DecisionInstanceEntity (ES model) to a DecisionInstanceDbModel (RDBMS model).
   *
   * @param entity the DecisionInstanceEntity from Elasticsearch
   * @return the corresponding DecisionInstanceDbModel for RDBMS
   */
  public static DecisionInstanceDbModel toRdbmsModel(final DecisionInstanceEntity entity) {
    final String decisionInstanceId = entity.getId();
    return new DecisionInstanceDbModel.Builder()
        .decisionInstanceId(decisionInstanceId)
        .decisionInstanceKey(entity.getKey())
        .state(mapState(entity.getState()))
        .evaluationDate(entity.getEvaluationDate())
        .evaluationFailure(entity.getEvaluationFailure())
        .evaluationFailureMessage(entity.getEvaluationFailureMessage())
        .result(entity.getResult())
        .flowNodeInstanceKey(entity.getElementInstanceKey())
        .flowNodeId(entity.getElementId())
        .processInstanceKey(entity.getProcessInstanceKey())
        .rootProcessInstanceKey(
            entity.getRootProcessInstanceKey() != null
                ? entity.getRootProcessInstanceKey()
                : entity.getProcessInstanceKey())
        .processDefinitionKey(entity.getProcessDefinitionKey())
        .processDefinitionId(entity.getBpmnProcessId())
        .decisionDefinitionKey(extractDecisionDefinitionKey(entity.getDecisionDefinitionId()))
        .decisionDefinitionId(entity.getDecisionId())
        .decisionRequirementsKey(entity.getDecisionRequirementsKey())
        .decisionRequirementsId(entity.getDecisionRequirementsId())
        .rootDecisionDefinitionKey(
            extractDecisionDefinitionKey(entity.getRootDecisionDefinitionId()))
        .decisionType(mapDecisionType(entity.getDecisionType()))
        .tenantId(entity.getTenantId())
        .partitionId(entity.getPartitionId())
        .evaluatedInputs(mapEvaluatedInputs(decisionInstanceId, entity.getEvaluatedInputs()))
        .evaluatedOutputs(mapEvaluatedOutputs(decisionInstanceId, entity.getEvaluatedOutputs()))
        .build();
  }

  private static DecisionInstanceState mapState(
      final io.camunda.webapps.schema.entities.dmn.DecisionInstanceState esState) {
    if (esState == null) {
      return null;
    }
    return switch (esState) {
      case EVALUATED -> DecisionInstanceState.EVALUATED;
      case FAILED -> DecisionInstanceState.FAILED;
    };
  }

  private static DecisionDefinitionType mapDecisionType(final DecisionType esDecisionType) {
    if (esDecisionType == null) {
      return null;
    }
    return switch (esDecisionType) {
      case DECISION_TABLE -> DecisionDefinitionType.DECISION_TABLE;
      case LITERAL_EXPRESSION -> DecisionDefinitionType.LITERAL_EXPRESSION;
      case UNKNOWN -> DecisionDefinitionType.UNKNOWN;
    };
  }

  private static Long extractDecisionDefinitionKey(final String decisionDefinitionId) {
    if (decisionDefinitionId == null || decisionDefinitionId.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(decisionDefinitionId);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private static List<EvaluatedInput> mapEvaluatedInputs(
      final String decisionInstanceId, final List<DecisionInstanceInputEntity> esInputs) {
    if (esInputs == null || esInputs.isEmpty()) {
      return Collections.emptyList();
    }
    return esInputs.stream()
        .map(
            input ->
                new EvaluatedInput(
                    decisionInstanceId, input.getId(), input.getName(), input.getValue()))
        .toList();
  }

  private static List<EvaluatedOutput> mapEvaluatedOutputs(
      final String decisionInstanceId, final List<DecisionInstanceOutputEntity> esOutputs) {
    if (esOutputs == null || esOutputs.isEmpty()) {
      return Collections.emptyList();
    }
    return esOutputs.stream()
        .map(
            output ->
                new EvaluatedOutput(
                    decisionInstanceId,
                    output.getId(),
                    output.getName(),
                    output.getValue(),
                    output.getRuleId(),
                    output.getRuleIndex()))
        .toList();
  }

  /**
   * Reads all decision instances from Elasticsearch and converts them to RDBMS models.
   *
   * @param esClient the Elasticsearch client
   * @return list of DecisionInstanceDbModel objects ready for RDBMS insertion
   */
  public static List<DecisionInstanceDbModel> readDecisionInstances(
      final ElasticsearchClient esClient) {
    return readAllDecisionInstancesFromEs(esClient).stream()
        .map(DecisionInstanceReader::toRdbmsModel)
        .toList();
  }
}
