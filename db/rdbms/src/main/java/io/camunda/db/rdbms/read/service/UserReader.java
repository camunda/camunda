/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.UserDbQuery;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.columns.UserSearchColumn;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserReader extends AbstractEntityReader<UserEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(UserReader.class);

  private final UserMapper userMapper;

  public UserReader(final UserMapper userMapper) {
    super(UserSearchColumn::findByProperty);
    this.userMapper = userMapper;
  }

  public Optional<UserEntity> findOne(final long userKey) {
    final var result = search(UserQuery.of(b -> b.filter(f -> f.key(userKey))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<UserEntity> search(final UserQuery query) {
    final var dbSort = convertSort(query.sort(), UserSearchColumn.USER_KEY);
    final var dbQuery =
        UserDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for users with filter {}", dbQuery);
    final var totalHits = userMapper.count(dbQuery);
    final var hits = userMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }
}
