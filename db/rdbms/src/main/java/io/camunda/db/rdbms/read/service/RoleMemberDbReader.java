/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.RoleMemberDbQuery;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.columns.RoleMemberSearchColumn;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.search.clients.reader.RoleMemberReader;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.RoleMemberQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleMemberDbReader extends AbstractEntityReader<RoleMemberEntity>
    implements RoleMemberReader {

  private static final Logger LOG = LoggerFactory.getLogger(RoleDbReader.class);

  private final RoleMapper roleMapper;

  public RoleMemberDbReader(final RoleMapper roleMapper) {
    super(RoleMemberSearchColumn.values());
    this.roleMapper = roleMapper;
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> search(
      final RoleMemberQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return new SearchQueryResult.Builder<RoleMemberEntity>().total(0).items(List.of()).build();
    }

    final var dbSort = convertSort(query.sort(), RoleMemberSearchColumn.ENTITY_ID);
    final var dbQuery =
        RoleMemberDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for roles with filter {}", dbQuery);
    final var totalHits = roleMapper.countMembers(dbQuery);
    final var hits = roleMapper.searchMembers(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  private RoleMemberEntity map(final RoleMemberDbModel model) {
    return new RoleMemberEntity(model.entityId(), EntityType.valueOf(model.entityType()));
  }
}
