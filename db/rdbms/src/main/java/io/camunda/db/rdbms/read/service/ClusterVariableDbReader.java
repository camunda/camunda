/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.ClusterVariableDbQuery;
import io.camunda.db.rdbms.sql.ClusterVariableMapper;
import io.camunda.db.rdbms.sql.columns.ClusterVariableSearchColumn;
import io.camunda.search.clients.reader.ClusterVariableReader;
import io.camunda.search.entities.ClusterVariableEntity;
import io.camunda.search.entities.ClusterVariableScope;
import io.camunda.search.query.ClusterVariableQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.util.ClusterVariableUtil;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterVariableDbReader extends AbstractEntityReader<ClusterVariableEntity>
    implements ClusterVariableReader {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterVariableDbReader.class);

  private final ClusterVariableMapper clusterVariableMapper;

  public ClusterVariableDbReader(
      final ClusterVariableMapper clusterVariableMapper, final RdbmsReaderConfig readerConfig) {
    super(ClusterVariableSearchColumn.values(), readerConfig);
    this.clusterVariableMapper = clusterVariableMapper;
  }

  @Override
  public SearchQueryResult<ClusterVariableEntity> search(
      final ClusterVariableQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), ClusterVariableSearchColumn.VAR_NAME);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final ClusterVariableDbQuery.Builder dbQueryBuilder = new ClusterVariableDbQuery.Builder();

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.CLUSTER_VARIABLE.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    dbQueryBuilder
        .filter(query.filter())
        .tenancyEnabled(resourceAccessChecks.tenantCheck().enabled())
        .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
        .authorizedResourceIds(authorizedResourceIds)
        .sort(dbSort)
        .page(dbPage);

    final var dbQuery = dbQueryBuilder.build();
    LOG.trace("[RDBMS DB] Search for cluster variables with filter {}", query);
    final var totalHits = clusterVariableMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = clusterVariableMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public SearchQueryResult<ClusterVariableEntity> search(final ClusterVariableQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public ClusterVariableEntity getTenantScopedClusterVariable(
      final String name, final String tenant, final ResourceAccessChecks resourceAccessChecks) {
    return clusterVariableMapper.get(
        ClusterVariableUtil.generateID(name, tenant, ClusterVariableScope.TENANT));
  }

  @Override
  public ClusterVariableEntity getGloballyScopedClusterVariable(
      final String name, final ResourceAccessChecks resourceAccessChecks) {
    return clusterVariableMapper.get(
        ClusterVariableUtil.generateID(name, null, ClusterVariableScope.GLOBAL));
  }

  @Override
  protected boolean noTenantAccess(final ResourceAccessChecks resourceAccessChecks) {
    // return always false => even when the principal has not any tenant assigned
    // they still may have access to globally scoped cluster variables
    return false;
  }
}
