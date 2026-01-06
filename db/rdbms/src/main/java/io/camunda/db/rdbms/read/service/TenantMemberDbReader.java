/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.TenantMemberDbQuery;
import io.camunda.db.rdbms.sql.TenantMapper;
import io.camunda.db.rdbms.sql.columns.TenantMemberSearchColumn;
import io.camunda.db.rdbms.write.domain.TenantMemberDbModel;
import io.camunda.search.clients.reader.TenantMemberReader;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantMemberQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantMemberDbReader extends AbstractEntityReader<TenantMemberEntity>
    implements TenantMemberReader {
  private static final Logger LOG = LoggerFactory.getLogger(TenantMemberDbReader.class);

  private final TenantMapper tenantMapper;

  public TenantMemberDbReader(final TenantMapper tenantMapper) {
    super(TenantMemberSearchColumn.values());
    this.tenantMapper = tenantMapper;
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> search(
      final TenantMemberQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return new SearchQueryResult.Builder<TenantMemberEntity>().total(0).items(List.of()).build();
    }

    final var dbSort =
        convertSort(
            query.sort(),
            TenantMemberSearchColumn.ENTITY_ID,
            TenantMemberSearchColumn.ENTITY_TYPE,
            TenantMemberSearchColumn.TENANT_ID);
    final var dbQuery =
        TenantMemberDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for tenants with filter {}", dbQuery);
    final var totalHits = tenantMapper.countMembers(dbQuery);
    final var hits = tenantMapper.searchMembers(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  private TenantMemberEntity map(final TenantMemberDbModel model) {
    // todo use EntityType as enum
    return new TenantMemberEntity(
        model.tenantId(), model.entityId(), EntityType.valueOf(model.entityType()));
  }
}
