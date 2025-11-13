/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.ClusterVariableDbQuery;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.columns.ClusterVariableSearchColumn;
import io.camunda.search.clients.reader.ClusterVariableReader;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.filter.ClusterVariableFilter.Builder;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.ClusterVariableSort;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterVariableDbReader extends AbstractEntityReader<ClusterVariableEntity>
    implements ClusterVariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterVariableDbReader.class);

  private final ClusterVariableMapper clusterVariableMapper;

  public ClusterVariableDbReader(final ClusterVariableMapper clusterVariableMapper) {
    super(ClusterVariableSearchColumn.values());
    this.clusterVariableMapper = clusterVariableMapper;
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> search(
      final ClusterVariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), ClusterVariableSearchColumn.VAR_NAME);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbQuery =
        ClusterVariableDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));
    LOG.trace("[RDBMS DB] Search for cluster variables with filter {}", query);
    final var totalHits = clusterVariableMapper.count(dbQuery);
    final var hits = clusterVariableMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public SearchQueryResult<ClusterVariableEntity> search(final ClusterVariableQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public ClusterVariableEntity getTenantScopedClusterVariable(
      final String tenant, final String name) {
    return search(
            new ClusterVariableQuery(
                new Builder().resourceIds(tenant).scopes("TENANT").names(name).build(),
                ClusterVariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(1))))
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  @Override
  public ClusterVariableEntity getGloballyScopedClusterVariable(final String name) {
    return search(
            new ClusterVariableQuery(
                new Builder().scopes("GLOBAL").names(name).build(),
                ClusterVariableSort.of(b -> b),
                SearchQueryPage.of(b -> b.from(0).size(1))))
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }
}
