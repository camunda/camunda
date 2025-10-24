/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.BatchOperationDbQuery;
import io.camunda.db.rdbms.read.mapper.BatchOperationEntityMapper;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.columns.BatchOperationSearchColumn;
import io.camunda.search.clients.reader.BatchOperationReader;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationDbReader extends AbstractEntityReader<BatchOperationEntity>
    implements BatchOperationReader {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationDbReader.class);

  private final BatchOperationMapper batchOperationMapper;

  public BatchOperationDbReader(final BatchOperationMapper batchOperationMapper) {
    super(BatchOperationSearchColumn.values());
    this.batchOperationMapper = batchOperationMapper;
  }

  public boolean exists(final String batchOperationKey) {
    final var query =
        new BatchOperationDbQuery.Builder()
            .filter(b -> b.batchOperationKeys(batchOperationKey))
            .build();

    return batchOperationMapper.count(query) == 1;
  }

  @Override
  public BatchOperationEntity getById(
      final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(id).orElse(null);
  }

  @Override
  public SearchQueryResult<BatchOperationEntity> search(
      final BatchOperationQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), BatchOperationSearchColumn.BATCH_OPERATION_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbQuery =
        BatchOperationDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for batch operations with filter {}", dbQuery);
    final var totalHits = batchOperationMapper.count(dbQuery);
    final var hits =
        batchOperationMapper.search(dbQuery).stream()
            .map(BatchOperationEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<BatchOperationEntity> findOne(final String batchOperationKey) {
    final var result =
        search(BatchOperationQuery.of(b -> b.filter(f -> f.batchOperationKeys(batchOperationKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<BatchOperationEntity> search(final BatchOperationQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
