/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.DecisionDefinitionDbQuery;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper;
import io.camunda.db.rdbms.sql.DecisionDefinitionMapper.DecisionDefinitionSearchColumn;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionDefinitionReader extends AbstractEntityReader<DecisionDefinitionEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(DecisionDefinitionReader.class);

  private final DecisionDefinitionMapper decisionDefinitionMapper;

  public DecisionDefinitionReader(final DecisionDefinitionMapper decisionDefinitionMapper) {
    super(DecisionDefinitionSearchColumn::findByProperty);
    this.decisionDefinitionMapper = decisionDefinitionMapper;
  }

  public Optional<DecisionDefinitionEntity> findOne(final long decisionDefinitionKey) {
    final var result =
        search(
            DecisionDefinitionQuery.of(
                b -> b.filter(f -> f.decisionDefinitionKeys(decisionDefinitionKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<DecisionDefinitionEntity> search(final DecisionDefinitionQuery query) {
    final var dbSort =
        convertSort(query.sort(), DecisionDefinitionSearchColumn.DECISION_DEFINITION_KEY);
    final var dbQuery =
        DecisionDefinitionDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for decision definition with filter {}", dbQuery);
    final var totalHits = decisionDefinitionMapper.count(dbQuery);
    final var hits = decisionDefinitionMapper.search(dbQuery);
    return new SearchQueryResult<>(totalHits.intValue(), hits, extractSortValues(hits, dbSort));
  }
}
