/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.RoleDbQuery;
import io.camunda.db.rdbms.sql.RoleMapper;
import io.camunda.db.rdbms.sql.columns.RoleSearchColumn;
import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.search.clients.reader.RoleReader;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleDbReader extends AbstractEntityReader<RoleEntity> implements RoleReader {

  private static final Logger LOG = LoggerFactory.getLogger(RoleDbReader.class);

  private final RoleMapper roleMapper;

  public RoleDbReader(final RoleMapper roleMapper) {
    super(RoleSearchColumn.values());
    this.roleMapper = roleMapper;
  }

  @Override
  public RoleEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(id).orElse(null);
  }

  @Override
  public SearchQueryResult<RoleEntity> search(
      final RoleQuery query, final ResourceAccessChecks resourceAccessChecks) {
    if (shouldReturnEmptyResult(query.filter(), resourceAccessChecks)) {
      return new SearchQueryResult.Builder<RoleEntity>().total(0).items(List.of()).build();
    }

    final var dbSort = convertSort(query.sort(), RoleSearchColumn.ROLE_ID);
    final var dbQuery =
        RoleDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for roles with filter {}", dbQuery);
    final var totalHits = roleMapper.count(dbQuery);
    final var hits = roleMapper.search(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<RoleEntity> findOne(final String roleId) {
    final var result = search(RoleQuery.of(b -> b.filter(f -> f.roleId(roleId))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<RoleEntity> search(final RoleQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  private RoleEntity map(final RoleDbModel model) {
    return new RoleEntity(model.roleKey(), model.roleId(), model.name(), model.description());
  }

  private boolean shouldReturnEmptyResult(
      final RoleFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    return (filter.roleIds() != null && filter.roleIds().isEmpty())
        || (filter.memberIds() != null && filter.memberIds().isEmpty()
            || shouldReturnEmptyResult(resourceAccessChecks));
  }
}
