/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.DeployedResourceDbQuery;
import io.camunda.db.rdbms.sql.DeployedResourceMapper;
import io.camunda.db.rdbms.sql.columns.DeployedResourceSearchColumn;
import io.camunda.search.clients.reader.DeployedResourceReader;
import io.camunda.search.entities.DeployedResourceEntity;
import io.camunda.search.query.DeployedResourceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeployedResourceDbReader extends AbstractEntityReader<DeployedResourceEntity>
    implements DeployedResourceReader {

  private static final Logger LOG = LoggerFactory.getLogger(DeployedResourceDbReader.class);

  private final DeployedResourceMapper deployedResourceMapper;

  public DeployedResourceDbReader(
      final DeployedResourceMapper deployedResourceMapper, final RdbmsReaderConfig readerConfig) {
    super(DeployedResourceSearchColumn.values(), readerConfig);
    this.deployedResourceMapper = deployedResourceMapper;
  }

  @Override
  public DeployedResourceEntity getByKey(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Get resource with resource key {}", key);
    return deployedResourceMapper.get(key);
  }

  @Override
  public DeployedResourceEntity getByKeyMetadata(
      final long key, final ResourceAccessChecks resourceAccessChecks) {
    LOG.trace("[RDBMS DB] Get resource metadata (no content) with resource key {}", key);
    return deployedResourceMapper.getMetadata(key);
  }

  @Override
  public SearchQueryResult<DeployedResourceEntity> search(
      final DeployedResourceQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), DeployedResourceSearchColumn.RESOURCE_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.RESOURCE.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        DeployedResourceDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for deployed resources with filter {}", dbQuery);
    return executePagedQuery(
        () -> deployedResourceMapper.count(dbQuery),
        () -> deployedResourceMapper.search(dbQuery),
        dbPage,
        dbSort);
  }

  public SearchQueryResult<DeployedResourceEntity> search(final DeployedResourceQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
