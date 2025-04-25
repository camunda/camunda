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
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupReader extends AbstractEntityReader<GroupEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(GroupReader.class);

  private final GroupMapper groupMapper;

  public GroupReader(final GroupMapper groupMapper) {
    super(GroupSearchColumn::findByProperty);
    this.groupMapper = groupMapper;
  }

  public Optional<GroupEntity> findOne(final String groupId) {
    final var result = search(GroupQuery.of(b -> b.filter(f -> f.groupId(groupId))));
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
    return new GroupEntity(
        model.groupKey(),
        model.groupId(),
        model.name(),
        model.description(),
        model.members().stream().map(GroupMemberDbModel::entityId).collect(Collectors.toSet()));
  }
}
