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
import io.camunda.db.rdbms.sql.DecisionRequirementsMapper.DecisionRequirementsSearchColumn;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionRequirementsReader extends AbstractEntityReader<DecisionRequirementsEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionRequirementsReader.class);

  private final DecisionRequirementsMapper decisionRequirementsMapper;

  public DecisionRequirementsReader(final DecisionRequirementsMapper decisionRequirementsMapper) {
    super(DecisionRequirementsSearchColumn::findByProperty);
    this.decisionRequirementsMapper = decisionRequirementsMapper;
  }

  public Optional<DecisionRequirementsEntity> findOne(final long decisionRequirementsKey) {
    final var result =
        search(
            DecisionRequirementsQuery.of(
                b ->
                    b.filter(f -> f.decisionRequirementsKeys(decisionRequirementsKey))
                        .resultConfig(c -> c.includeXml(true))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
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
    return new SearchQueryResult<>(totalHits.intValue(), hits, extractSortValues(hits, dbSort));
  }
}
