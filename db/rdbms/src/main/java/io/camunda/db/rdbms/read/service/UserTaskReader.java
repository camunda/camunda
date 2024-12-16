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
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.UserTaskSort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserTaskReader {

  private static final Logger LOG = LoggerFactory.getLogger(UserTaskReader.class);

  private final UserTaskMapper userTaskMapper;

  public UserTaskReader(final UserTaskMapper userTaskMapper) {
    this.userTaskMapper = userTaskMapper;
  }

  public UserTaskEntity findOne(final Long key) {
    LOG.trace("[RDBMS DB] Search for user task with key {}", key);
    return search(
            new UserTaskDbQuery(
                new UserTaskFilter.Builder().userTaskKeys(key).build(),
                UserTaskSort.of(b -> b),
                SearchQueryPage.of(b -> b)))
        .hits()
        .getFirst();
  }

  public SearchResult search(final UserTaskDbQuery filter) {
    LOG.trace("[RDBMS DB] Search for user task with filter {}", filter);
    final var totalHits = userTaskMapper.count(filter);
    final var hits =
        userTaskMapper.search(filter).stream().map(UserTaskEntityMapper::toEntity).toList();
    return new SearchResult(hits, totalHits.intValue());
  }

  public record SearchResult(List<UserTaskEntity> hits, Integer total) {}
}
