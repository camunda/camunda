/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.GroupDbQuery;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.columns.GroupSearchColumn;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.search.clients.reader.GroupReader;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupDbReader extends AbstractEntityReader<GroupEntity> implements GroupReader {

  private static final Logger LOG = LoggerFactory.getLogger(GroupDbReader.class);

  private final GroupMapper groupMapper;

  public GroupDbReader(final GroupMapper groupMapper) {
    super(GroupSearchColumn.values());
    this.groupMapper = groupMapper;
  }

  public Optional<GroupEntity> findOne(final String groupId) {
    final var result = search(GroupQuery.of(b -> b.filter(f -> f.groupIds(groupId))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<GroupEntity> search(final GroupQuery query) {
    final var dbSort = convertSort(query.sort(), GroupSearchColumn.GROUP_ID);
    final var dbQuery =
        GroupDbQuery.of(
            b -> b.filter(query.filter()).sort(dbSort).page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for groups with filter {}", dbQuery);
    final var totalHits = groupMapper.count(dbQuery);
    final var hits = groupMapper.search(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  private GroupEntity map(final GroupDbModel model) {
    return new GroupEntity(model.groupKey(), model.groupId(), model.name(), model.description());
  }

  @Override
  public GroupEntity getByKey(final String key, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(key).orElse(null);
  }

  @Override
  public SearchQueryResult<GroupEntity> search(
      final GroupQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return search(query);
  }
}
