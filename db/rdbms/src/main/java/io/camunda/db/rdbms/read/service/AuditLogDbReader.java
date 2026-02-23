/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.AuditLogAuthorizationFilter;
import io.camunda.db.rdbms.read.domain.AuditLogDbQuery;
import io.camunda.db.rdbms.read.mapper.AuditLogEntityMapper;
import io.camunda.db.rdbms.sql.AuditLogMapper;
import io.camunda.db.rdbms.sql.columns.AuditLogSearchColumn;
import io.camunda.search.clients.reader.AuditLogReader;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditLogDbReader extends AbstractEntityReader<AuditLogEntity>
    implements AuditLogReader {

  private static final Logger LOG = LoggerFactory.getLogger(AuditLogDbReader.class);

  private final AuditLogMapper auditLogMapper;

  public AuditLogDbReader(
      final AuditLogMapper auditLogMapper, final RdbmsReaderConfig readerConfig) {
    super(AuditLogSearchColumn.values(), readerConfig);
    this.auditLogMapper = auditLogMapper;
  }

  @Override
  public AuditLogEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    final var result = search(AuditLogQuery.of(b -> b.filter(f -> f.auditLogKeys(id))));
    return Optional.ofNullable(result.items())
        .flatMap(items -> items.stream().findFirst())
        .orElse(null);
  }

  @Override
  public SearchQueryResult<AuditLogEntity> search(
      final AuditLogQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), AuditLogSearchColumn.TIMESTAMP);

    // Only check for resource access, not tenant access.
    // Audit logs can be GLOBAL scoped (e.g., ADMIN category operations) and don't require
    // tenant access. The SQL query already handles this by allowing TENANT_SCOPE = 'GLOBAL'
    // when authorizedTenantIds is empty.
    if (noResourceAccess(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizationFilter =
        AuditLogAuthorizationFilter.of(resourceAccessChecks.authorizationCheck());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        AuditLogDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizationFilter(authorizationFilter)
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for audit logs with filter {}", dbQuery);
    final var totalHits = auditLogMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits =
        auditLogMapper.search(dbQuery).stream().map(AuditLogEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public SearchQueryResult<AuditLogEntity> search(final AuditLogQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
