/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.domain.GroupMemberDbQuery;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.columns.GroupMemberSearchColumn;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupMemberDbReader extends AbstractEntityReader<GroupMemberEntity>
    implements GroupMemberReader {

  private static final Logger LOG = LoggerFactory.getLogger(GroupMemberDbReader.class);

  private final GroupMapper groupMapper;

  public GroupMemberDbReader(final GroupMapper groupMapper) {
    super(null);
    this.groupMapper = groupMapper;
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> search(
      final GroupQuery query, final ResourceAccessChecks resourceAccessChecks) {
    if (shouldReturnEmptyResult(query.filter(), resourceAccessChecks)) {
      return new SearchQueryResult.Builder<GroupMemberEntity>().total(0).items(List.of()).build();
    }

    final var dbSort = convertSort(query.sort(), GroupMemberSearchColumn.ENTITY_ID);

    final var dbQuery =
        GroupMemberDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(resourceAccessChecks.getAuthorizedResourceIds())
                    .sort(dbSort)
                    .page(convertPaging(dbSort, query.page())));

    LOG.trace("[RDBMS DB] Search for tenants with filter {}", dbQuery);
    final var totalHits = groupMapper.countMembers(dbQuery);
    final var hits = groupMapper.searchMembers(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  private GroupMemberEntity map(final GroupMemberDbModel model) {
    // todo use EntityType as enum
    return new GroupMemberEntity(model.entityId(), EntityType.valueOf(model.entityType()));
  }

  private boolean shouldReturnEmptyResult(
      final GroupFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    return (filter.memberIds() != null && filter.memberIds().isEmpty())
        || (resourceAccessChecks.authorizationCheck().enabled()
            && resourceAccessChecks.getAuthorizedResourceIds().isEmpty());
  }
}
