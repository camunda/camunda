/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.DecisionRequirementsDbQuery;
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper;
import io.camunda.db.rdbms.sql.columns.DecisionRequirementsSearchColumn;
import io.camunda.search.clients.reader.DecisionRequirementsReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRequirementsDbReader extends AbstractEntityReader<DecisionRequirementsEntity>
    implements DecisionRequirementsReader {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionRequirementsDbReader.class);

  private final DecisionRequirementsMapper decisionRequirementsMapper;

  public DecisionRequirementsDbReader(final DecisionRequirementsMapper decisionRequirementsMapper) {
    super(DecisionRequirementsSearchColumn.values());
    this.decisionRequirementsMapper = decisionRequirementsMapper;
  }

  public Optional<DecisionRequirementsEntity> findOne(
      final long decisionRequirementsKey, final boolean includeXml) {
    final var result =
        search(
            DecisionRequirementsQuery.of(
                b ->
                    b.filter(f -> f.decisionRequirementsKeys(decisionRequirementsKey))
                        .resultConfig(c -> c.includeXml(includeXml))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public Optional<DecisionRequirementsEntity> findOne(final long decisionRequirementsKey) {
    return findOne(decisionRequirementsKey, true);
  }

  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query) {
    final var dbSort =
        convertSort(query.sort(), DecisionRequirementsSearchColumn.DECISION_REQUIREMENTS_KEY);
    final var dbQuery =
        DecisionRequirementsDbQuery.of(
            b ->
                b.filter(query.filter())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page()))
                    .resultConfig(query.resultConfig()));

    LOG.trace("[RDBMS DB] Search for decision requirements with filter {}", dbQuery);
    final var totalHits = decisionRequirementsMapper.count(dbQuery);
    final var hits = decisionRequirementsMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  @Override
  public DecisionRequirementsEntity getByKey(
      final String key, final boolean includeXml, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(Long.parseLong(key), includeXml).orElse(null);
  }

  @Override
  public DecisionRequirementsEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getByKey(key, false, resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return search(query);
  }
}
