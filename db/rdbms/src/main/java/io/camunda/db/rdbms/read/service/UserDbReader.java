/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.UserDbQuery;
import io.camunda.db.rdbms.sql.UserMapper;
import io.camunda.db.rdbms.sql.columns.UserSearchColumn;
import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDbReader extends AbstractEntityReader<UserEntity> implements UserReader {

  private static final Logger LOG = LoggerFactory.getLogger(UserDbReader.class);

  private final UserMapper userMapper;

  public UserDbReader(final UserMapper userMapper, final RdbmsReaderConfig readerConfig) {
    super(UserSearchColumn.values(), readerConfig);
    this.userMapper = userMapper;
  }

  @Override
  public UserEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOneByUsername(id).orElse(null);
  }

  @Override
  public SearchQueryResult<UserEntity> search(
      final UserQuery query, final ResourceAccessChecks resourceAccessChecks) {
    final var dbSort = convertSort(query.sort(), UserSearchColumn.USER_KEY);

    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return buildSearchQueryResult(0, List.of(), dbSort);
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.USER.name(), List.of());
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        UserDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for users with filter {}", dbQuery);
    final var totalHits = userMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = userMapper.search(dbQuery);
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<UserEntity> findOneByUsername(final String username) {
    final var result = search(UserQuery.of(b -> b.filter(f -> f.usernames(username))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public Optional<UserEntity> findOne(final long userKey) {
    final var result = search(UserQuery.of(b -> b.filter(f -> f.key(userKey))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<UserEntity> search(final UserQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }
}
