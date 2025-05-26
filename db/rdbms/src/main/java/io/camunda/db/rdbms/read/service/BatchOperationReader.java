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
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationReader extends AbstractEntityReader<BatchOperationEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationReader.class);

  private final BatchOperationMapper batchOperationMapper;

  public BatchOperationReader(final BatchOperationMapper batchOperationMapper) {
    super(BatchOperationSearchColumn::findByProperty);
    this.batchOperationMapper = batchOperationMapper;
  }

  public boolean exists(final String batchOperationKey) {
    final var query =
        new BatchOperationDbQuery.Builder()
            .filter(b -> b.batchOperationIds(batchOperationKey))
            .build();

    return batchOperationMapper.count(query) == 1;
  }

  public Optional<BatchOperationEntity> findOne(final String batchOperationKey) {
    final var result =
        search(BatchOperationQuery.of(b -> b.filter(f -> f.batchOperationIds(batchOperationKey))));
    return Optional.ofNullable(result.items()).flatMap(it -> it.stream().findFirst());
  }

  public SearchQueryResult<BatchOperationEntity> search(final BatchOperationQuery query) {
    final var dbSort = convertSort(query.sort(), BatchOperationSearchColumn.BATCH_OPERATION_KEY);
    final var dbQuery =
        BatchOperationDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for batch operations with filter {}", dbQuery);
    final var totalHits = batchOperationMapper.count(dbQuery);
    final var groupedHits = groupAndSumToList(batchOperationMapper.search(dbQuery));
    final var hits = groupedHits.stream()
            .map(BatchOperationEntityMapper::toEntity)
            .toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  private static Collection<BatchOperationDbModel> groupAndSumToList(
      final List<BatchOperationDbModel> models) {
    return models.stream()
        .collect(Collectors.toMap(
            BatchOperationDbModel::batchOperationKey,
            Function.identity(),
            (a, b) -> new BatchOperationDbModel(
                a.batchOperationKey(),
                a.state(),
                a.operationType(),
                a.startDate(),
                a.endDate(),
                a.operationsTotalCount() + b.operationsTotalCount(),
                a.operationsFailedCount() + b.operationsFailedCount(),
                a.operationsCompletedCount() + b.operationsCompletedCount(),
                a.partitionId(),
                true
            )
        ))
        .values();
  }
}
