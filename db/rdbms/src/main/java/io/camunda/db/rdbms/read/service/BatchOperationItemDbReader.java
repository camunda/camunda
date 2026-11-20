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
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
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
    return search(query, ResourceAccessChecks.disabled());
  }

  @Override
  public SearchQueryResult<BatchOperationItemEntity> search(
      final BatchOperationItemQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort =
        convertSort(
            query.sort(),
            BatchOperationItemSearchColumn.BATCH_OPERATION_KEY,
            BatchOperationItemSearchColumn.ITEM_KEY);
    if (hasNoBatchReadAccess(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }
    final var dbQuery =
        BatchOperationItemDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for batch operation items with filter {}", dbQuery);
    final var totalHits = batchOperationMapper.countItems(dbQuery);
    final var hits = batchOperationMapper.searchItems(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  /**
   * Batch operation items carry no tenant, so only the authorization check applies, as on
   * Elasticsearch/OpenSearch.
   */
  private static boolean hasNoBatchReadAccess(final ResourceAccessChecks resourceAccessChecks) {
    return resourceAccessChecks.authorizationCheck().enabled()
        && resourceAccessChecks.getAuthorizedResourceIds().isEmpty();
  }
}
