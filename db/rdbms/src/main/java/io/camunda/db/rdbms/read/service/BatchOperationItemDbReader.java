/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.BatchOperationItemDbQuery;
import io.camunda.db.rdbms.sql.BatchOperationMapper;
import io.camunda.db.rdbms.sql.columns.BatchOperationItemSearchColumn;
import io.camunda.search.clients.reader.BatchOperationItemReader;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationItemDbReader extends AbstractEntityReader<BatchOperationItemEntity>
    implements BatchOperationItemReader {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationItemDbReader.class);

  private final BatchOperationMapper batchOperationMapper;

  public BatchOperationItemDbReader(
      final BatchOperationMapper batchOperationMapper, final RdbmsReaderConfig readerConfig) {
    super(BatchOperationItemSearchColumn.values(), readerConfig);
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

    // batch operation items carry no tenant, so only the BATCH permission decides access
    if (noResourceAccess(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }
    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.BATCH.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        BatchOperationItemDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for batch operation items with filter {}", dbQuery);
    return executePagedQuery(
        () -> batchOperationMapper.countItems(dbQuery),
        () -> batchOperationMapper.searchItems(dbQuery),
        dbPage,
        dbSort);
  }
}
