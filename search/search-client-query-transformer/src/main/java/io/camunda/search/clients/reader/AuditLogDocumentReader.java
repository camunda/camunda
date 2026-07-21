/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.aggregation.result.AuditLogLatestSuccessfulAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationResult;
import io.camunda.search.filter.AuditLogFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.AuditLogLatestSuccessfulQuery;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.LinkedHashSet;
import java.util.List;

public class AuditLogDocumentReader extends DocumentBasedReader implements AuditLogReader {

  public AuditLogDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public AuditLogEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            AuditLogQuery.of(b -> b.filter(f -> f.auditLogKeys(id)).singleResult()),
            io.camunda.webapps.schema.entities.auditlog.AuditLogEntity.class);
  }

  @Override
  public List<AuditLogEntity> searchLatestSuccessfulByEntityKeys(
      final AuditLogEntityType entityType,
      final List<String> entityKeys,
      final ResourceAccessChecks resourceAccessChecks) {
    final var uniqueEntityKeys = List.copyOf(new LinkedHashSet<>(entityKeys));
    if (uniqueEntityKeys.isEmpty()) {
      return List.of();
    }

    final var filter =
        AuditLogFilter.of(
            f ->
                f.entityKeyOperations(Operation.in(uniqueEntityKeys))
                    .entityTypes(entityType.name())
                    .results(AuditLogOperationResult.SUCCESS.name()));
    final var query =
        new AuditLogLatestSuccessfulQuery(
            filter, SearchQueryPage.of(page -> page.size(uniqueEntityKeys.size())));
    return getSearchExecutor()
        .aggregate(query, AuditLogLatestSuccessfulAggregationResult.class, resourceAccessChecks)
        .items();
  }

  @Override
  public SearchQueryResult<AuditLogEntity> search(
      final AuditLogQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.auditlog.AuditLogEntity.class,
            resourceAccessChecks);
  }
}
