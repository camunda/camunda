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

  private final UserMapper processDefinitionMapper;

  public UserReader(final UserMapper processDefinitionMapper) {
    super(UserSearchColumn::findByProperty);
    this.processDefinitionMapper = processDefinitionMapper;
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
    final var totalHits = processDefinitionMapper.count(dbQuery);
    final var hits = processDefinitionMapper.search(dbQuery);
    return new SearchQueryResult<>(totalHits.intValue(), hits, extractSortValues(hits, dbSort));
  }
}
