/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.VariableDbQuery;
import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.db.rdbms.sql.columns.VariableSearchColumn;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.VariableFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableReader extends AbstractEntityReader<VariableEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(VariableReader.class);

  private final VariableMapper variableMapper;

  public VariableReader(final VariableMapper variableMapper) {
    super(VariableSearchColumn::findByProperty);
    this.variableMapper = variableMapper;
  }

  public VariableEntity findOne(final Long key) {
    return search(
            new VariableQuery(
                new Builder().variableKeys(key).build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(1))))
        .items()
        .getFirst();
  }

  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    final var dbSort = convertSort(query.sort(), VariableSearchColumn.VAR_KEY);
    final var dbQuery =
        VariableDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));
    LOG.trace("[RDBMS DB] Search for variables with filter {}", query);
    final var totalHits = variableMapper.count(dbQuery);
    final var hits = variableMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public record SearchResult(List<VariableEntity> hits, Integer total) {}
}
