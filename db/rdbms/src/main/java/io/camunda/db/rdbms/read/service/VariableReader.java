/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.sql.VariableMapper;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.VariableFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(VariableReader.class);

  private final VariableMapper variableMapper;

  public VariableReader(final VariableMapper variableMapper) {
    this.variableMapper = variableMapper;
  }

  public VariableEntity findOne(final Long key) {
    return search(
            new VariableQuery(
                new Builder().variableKeys(key).build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(1))))
        .hits
        .getFirst();
  }

  public SearchResult search(final VariableQuery filter) {
    LOG.trace("[RDBMS DB] Search for variables with filter {}", filter);
    final var totalHits = variableMapper.count(filter);
    final var hits = variableMapper.search(filter);
    return new SearchResult(hits, totalHits.intValue());
  }

  public record SearchResult(List<VariableEntity> hits, Integer total) {}
}
