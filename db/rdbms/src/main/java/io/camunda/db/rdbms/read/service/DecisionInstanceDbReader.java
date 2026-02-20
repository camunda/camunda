/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.DecisionInstanceDbQuery;
import io.camunda.db.rdbms.sql.DecisionInstanceMapper;
import io.camunda.db.rdbms.sql.columns.DecisionInstanceSearchColumn;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedInput;
import io.camunda.db.rdbms.write.domain.DecisionInstanceDbModel.EvaluatedOutput;
import io.camunda.search.clients.reader.DecisionInstanceReader;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceInputEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceOutputEntity;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionInstanceDbReader extends AbstractEntityReader<DecisionInstanceEntity>
    implements DecisionInstanceReader {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionInstanceDbReader.class);

  private final DecisionInstanceMapper decisionInstanceMapper;

  public DecisionInstanceDbReader(
      final DecisionInstanceMapper decisionInstanceMapper, final RdbmsReaderConfig readerConfig) {
    super(DecisionInstanceSearchColumn.values(), readerConfig);
    this.decisionInstanceMapper = decisionInstanceMapper;
  }

  @Override
  public DecisionInstanceEntity getById(
      final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(id).orElse(null);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(
      final DecisionInstanceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), DecisionInstanceSearchColumn.DECISION_INSTANCE_ID);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.DECISION_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        DecisionInstanceDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for process instance with filter {}", dbQuery);
    final var totalHits = decisionInstanceMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = enhanceEntities(decisionInstanceMapper.search(dbQuery), query.resultConfig());

    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<DecisionInstanceEntity> findOne(final String decisionInstanceId) {
    LOG.trace("[RDBMS DB] Search for decision instance with key {}", decisionInstanceId);
    final var result =
        search(
            DecisionInstanceQuery.of(
                b ->
                    b.filter(f -> f.decisionInstanceIds(decisionInstanceId))
                        .resultConfig(
                            r -> r.includeEvaluatedInputs(true).includeEvaluatedOutputs(true))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  /**
   * Based on the result config, re batch-load here additional data (input, output values) with one
   * SQL each (if enabled).
   */
  private List<DecisionInstanceEntity> enhanceEntities(
      final List<DecisionInstanceEntity> intermediateResult,
      final DecisionInstanceQueryResultConfig resultConfig) {
    if (intermediateResult.isEmpty()) {
      return intermediateResult;
    }

    if (resultConfig == null
        || (!resultConfig.includeEvaluatedInputs() && !resultConfig.includeEvaluatedOutputs())) {
      return intermediateResult;
    }

    final Map<String, List<EvaluatedInput>> inputs = new HashMap<>();
    final Map<String, List<EvaluatedOutput>> outputs = new HashMap<>();
    final List<String> keys =
        intermediateResult.stream().map(DecisionInstanceEntity::decisionInstanceId).toList();
    if (resultConfig.includeEvaluatedInputs()) {
      inputs.putAll(
          decisionInstanceMapper.loadInputs(keys).stream()
              .collect(Collectors.groupingBy(EvaluatedInput::decisionInstanceId)));
    }
    if (resultConfig.includeEvaluatedOutputs()) {
      outputs.putAll(
          decisionInstanceMapper.loadOutputs(keys).stream()
              .collect(Collectors.groupingBy(EvaluatedOutput::decisionInstanceId)));
    }

    return intermediateResult.stream()
        .map(
            entity ->
                entity.toBuilder()
                    .evaluatedInputs(
                        mapInputList(inputs.getOrDefault(entity.decisionInstanceId(), List.of())))
                    .evaluatedOutputs(
                        mapOutputList(outputs.getOrDefault(entity.decisionInstanceId(), List.of())))
                    .build())
        .toList();
  }

  private List<DecisionInstanceInputEntity> mapInputList(final List<EvaluatedInput> inputList) {
    return inputList.stream()
        .map(i -> new DecisionInstanceInputEntity(i.id(), i.name(), i.value()))
        .toList();
  }

  private List<DecisionInstanceOutputEntity> mapOutputList(final List<EvaluatedOutput> outputList) {
    return outputList.stream()
        .map(
            o ->
                new DecisionInstanceOutputEntity(
                    o.id(), o.name(), o.value(), o.ruleId(), o.ruleIndex()))
        .toList();
  }
}
