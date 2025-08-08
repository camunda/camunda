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
import io.camunda.search.clients.reader.VariableReader;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.VariableFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableDbReader extends AbstractEntityReader<VariableEntity>
    implements VariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(VariableDbReader.class);

  private final VariableMapper variableMapper;

  public VariableDbReader(final VariableMapper variableMapper) {
    super(VariableSearchColumn.values());
    this.variableMapper = variableMapper;
  }

  @Override
  public VariableEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(
      final VariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), VariableSearchColumn.VAR_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbQuery =
        VariableDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));
    LOG.trace("[RDBMS DB] Search for variables with filter {}", query);
    final var totalHits = variableMapper.count(dbQuery);
    final var hits = variableMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public VariableEntity findOne(final Long key) {
    return search(
            new VariableQuery(
                new Builder().variableKeys(key).build(),
                VariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(1))))
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  /**
   * Checks if the search result should be empty based on resource and tenant authorization.
   * Returns {@code true} if authorization is enabled but no authorized resource or tenant IDs are present.
   *
   * @param resourceAccessChecks the resource access checks containing authorization and tenant checks
   * @return {@code true} if the search result should be empty, {@code false
   */
  private boolean shouldReturnEmptyResult(final ResourceAccessChecks resourceAccessChecks) {
    return resourceAccessChecks.authorizationCheck().enabled()
            && resourceAccessChecks.getAuthorizedResourceIds().isEmpty()
        || resourceAccessChecks.tenantCheck().enabled()
            && resourceAccessChecks.getAuthorizedTenantIds().isEmpty();
  }

  public record SearchResult(List<VariableEntity> hits, Integer total) {}
}
