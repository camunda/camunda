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
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskReader extends AbstractEntityReader<UserTaskEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskReader.class);

  private final UserTaskMapper userTaskMapper;

  public UserTaskReader(final UserTaskMapper userTaskMapper) {
    super(UserTaskSearchColumn::findByProperty);
    this.userTaskMapper = userTaskMapper;
  }

  public Optional<UserTaskEntity> findOne(final long userTaskKey) {
    final var result =
        search(UserTaskQuery.of(b -> b.filter(f -> f.userTaskKeys(List.of(userTaskKey)))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    final var dbSort = convertSort(query.sort(), UserTaskSearchColumn.USER_TASK_KEY);
    final var dbQuery =
        UserTaskDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for users with filter {}", dbQuery);
    final var totalHits = userTaskMapper.count(dbQuery);
    final var hits =
        userTaskMapper.search(dbQuery).stream().map(UserTaskEntityMapper::toEntity).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
