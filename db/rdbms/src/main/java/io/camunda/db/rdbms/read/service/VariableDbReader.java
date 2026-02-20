/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VariableDbReader extends AbstractEntityReader<VariableEntity>
    implements VariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(VariableDbReader.class);

  private final VariableMapper variableMapper;

  public VariableDbReader(
      final VariableMapper variableMapper, final RdbmsReaderConfig readerConfig) {
    super(VariableSearchColumn.values(), readerConfig);
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

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.PROCESS_DEFINITION.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        VariableDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));
    LOG.trace("[RDBMS DB] Search for variables with filter {}", query);
    final var totalHits = variableMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

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
}
