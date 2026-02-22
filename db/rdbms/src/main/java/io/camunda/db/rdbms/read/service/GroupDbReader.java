/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.GroupDbQuery;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.columns.GroupSearchColumn;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.search.clients.reader.GroupReader;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupDbReader extends AbstractEntityReader<GroupEntity> implements GroupReader {

  private static final Logger LOG = LoggerFactory.getLogger(GroupDbReader.class);

  private final GroupMapper groupMapper;

  public GroupDbReader(final GroupMapper groupMapper, final RdbmsReaderConfig readerConfig) {
    super(GroupSearchColumn.values(), readerConfig);
    this.groupMapper = groupMapper;
  }

  @Override
  public GroupEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return findOne(id).orElse(null);
  }

  @Override
  public SearchQueryResult<GroupEntity> search(
      final GroupQuery query, final ResourceAccessChecks resourceAccessChecks) {

    if (shouldReturnEmptyResult(query.filter(), resourceAccessChecks)) {
      return new SearchQueryResult.Builder<GroupEntity>().total(0).items(List.of()).build();
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.GROUP.name(), List.of());
    final var dbSort = convertSort(query.sort(), GroupSearchColumn.GROUP_ID);
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        GroupDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for groups with filter {}", dbQuery);
    final var totalHits = groupMapper.count(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = groupMapper.search(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  public Optional<GroupEntity> findOne(final String groupId) {
    final var result = search(GroupQuery.of(b -> b.filter(f -> f.groupIds(groupId))));
    return Optional.ofNullable(result.items()).flatMap(items -> items.stream().findFirst());
  }

  public SearchQueryResult<GroupEntity> search(final GroupQuery query) {
    return search(query, ResourceAccessChecks.disabled());
  }

  private GroupEntity map(final GroupDbModel model) {
    return new GroupEntity(model.groupKey(), model.groupId(), model.name(), model.description());
  }

  private boolean shouldReturnEmptyResult(
      final GroupFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    return (filter.memberIds() != null && filter.memberIds().isEmpty())
        || shouldReturnEmptyResult(resourceAccessChecks);
  }
}
