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
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationReader extends AbstractEntityReader<BatchOperationEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationReader.class);

  private final BatchOperationMapper batchOperationMapper;

  public BatchOperationReader(final BatchOperationMapper batchOperationMapper) {
    super(BatchOperationSearchColumn::findByProperty);
    this.batchOperationMapper = batchOperationMapper;
  }

  public boolean exists(final Long batchOperationKey) {
    var query =
        new BatchOperationDbQuery.Builder()
            .filter(b -> b.batchOperationKeys(batchOperationKey))
            .build();

    return batchOperationMapper.count(query) == 1;
  }

  public SearchQueryResult<BatchOperationEntity> search(final BatchOperationQuery query) {
    final var dbSort = convertSort(query.sort(), BatchOperationSearchColumn.BATCH_OPERATION_KEY);
    final var dbQuery =
        BatchOperationDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for batch operations with filter {}", dbQuery);
    final var totalHits = batchOperationMapper.count(dbQuery);
    final var hits =
        batchOperationMapper.search(dbQuery).stream()
            .map(BatchOperationEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public List<BatchOperationItemEntity> getItems(final Long batchOperationKey) {

    return batchOperationMapper.getItems(batchOperationKey).stream().toList();
  }
}
