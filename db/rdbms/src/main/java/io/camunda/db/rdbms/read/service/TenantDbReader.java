/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.TenantDbQuery;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.columns.TenantSearchColumn;
import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.search.clients.reader.TenantReader;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantDbReader extends AbstractEntityReader<TenantEntity> implements TenantReader {

  private static final Logger LOG = LoggerFactory.getLogger(TenantDbReader.class);

  private final TenantMapper tenantMapper;

  public TenantDbReader(final TenantMapper tenantMapper) {
    super(TenantSearchColumn.values());
    this.tenantMapper = tenantMapper;
  }

  @Override
  public TenantEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(id).orElse(null);
  }

  @Override
  public SearchQueryResult<TenantEntity> search(
      final TenantQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(query.filter(), resourceAccessChecks)) {
      return new SearchQueryResult.Builder<TenantEntity>().total(0).items(List.of()).build();
    }

    final var dbSort = convertSort(query.sort(), TenantSearchColumn.TENANT_ID);
    final var dbQuery =
        TenantDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for tenants with filter {}", dbQuery);
    final var totalHits = tenantMapper.count(dbQuery);
    final var hits = tenantMapper.search(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<TenantEntity> findOne(final String tenantId) {
    final var result = search(TenantQuery.of(b -> b.filter(f -> f.tenantId(tenantId))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<TenantEntity> search(final TenantQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  private TenantEntity map(final TenantDbModel model) {
    return new TenantEntity(model.tenantKey(), model.tenantId(), model.name(), model.description());
  }

  private boolean shouldReturnEmptyResult(
      final TenantFilter filter, ResourceAccessChecks resourceAccessChecks) {
    return (filter.memberIds() != null && filter.memberIds().isEmpty())
        || (resourceAccessChecks.authorizationCheck().enabled()
            && resourceAccessChecks.getAuthorizedResourceIds().isEmpty());
  }
}
