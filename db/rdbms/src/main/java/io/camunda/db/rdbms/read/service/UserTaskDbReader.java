/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.UserTaskDbQuery;
import io.camunda.db.rdbms.read.mapper.UserTaskEntityMapper;
import io.camunda.db.rdbms.sql.UserTaskMapper;
import io.camunda.db.rdbms.sql.columns.UserTaskSearchColumn;
import io.camunda.search.clients.reader.UserTaskReader;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskDbReader extends AbstractEntityReader<UserTaskEntity>
    implements UserTaskReader {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskDbReader.class);

  private final UserTaskMapper userTaskMapper;

  public UserTaskDbReader(final UserTaskMapper userTaskMapper) {
    super(UserTaskSearchColumn.values());
    this.userTaskMapper = userTaskMapper;
  }

  @Override
  public UserTaskEntity getByKey(final long key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), UserTaskSearchColumn.USER_TASK_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var dbQuery =
        UserTaskDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .authorizedTenantIds(resourceAccessChecks.getAuthorizedTenantIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for users with filter {}", dbQuery);
    final var totalHits = userTaskMapper.count(dbQuery);
    final var hits =
        userTaskMapper.search(dbQuery).stream().map(UserTaskEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<UserTaskEntity> findOne(final long userTaskKey) {
    final var result =
        search(UserTaskQuery.of(b -> b.filter(f -> f.userTaskKeys(List.of(userTaskKey)))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
