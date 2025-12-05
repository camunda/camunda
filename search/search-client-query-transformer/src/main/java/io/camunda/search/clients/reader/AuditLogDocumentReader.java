/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

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
  public SearchQueryResult<AuditLogEntity> search(
      final AuditLogQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.auditlog.AuditLogEntity.class,
            resourceAccessChecks);
  }
}
