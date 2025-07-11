/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.BatchOperationItemDbQuery;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.columns.BatchOperationItemSearchColumn;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.SearchQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationItemDbReader extends AbstractEntityReader<BatchOperationItemEntity>
    implements BatchOperationItemReader {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationItemDbReader.class);

  private final BatchOperationMapper batchOperationMapper;

  public BatchOperationItemDbReader(final BatchOperationMapper batchOperationMapper) {
    super(BatchOperationItemSearchColumn.values());
    this.batchOperationMapper = batchOperationMapper;
  }

  public SearchQueryResult<BatchOperationItemEntity> search(final BatchOperationItemQuery query) {
    final var dbSort =
        convertSort(
            query.sort(),
            BatchOperationItemSearchColumn.BATCH_OPERATION_KEY,
            BatchOperationItemSearchColumn.ITEM_KEY);
    final var dbQuery =
        BatchOperationItemDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for batch operation items with filter {}", dbQuery);
    final var totalHits = batchOperationMapper.countItems(dbQuery);
    final var hits = batchOperationMapper.searchItems(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  @Override
  public BatchOperationItemEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("BatchOperationItemReader#getByKey not supported.");
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> search(
      final BatchOperationItemQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return search(query);
  }
}
