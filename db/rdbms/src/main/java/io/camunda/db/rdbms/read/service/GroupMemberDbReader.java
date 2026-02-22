/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import io.camunda.db.rdbms.read.RdbmsReaderConfig;
import io.camunda.db.rdbms.read.domain.GroupMemberDbQuery;
import io.camunda.db.rdbms.sql.GroupMapper;
import io.camunda.db.rdbms.sql.columns.GroupMemberSearchColumn;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.search.clients.reader.GroupMemberReader;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupMemberDbReader extends AbstractEntityReader<GroupMemberEntity>
    implements GroupMemberReader {

  private static final Logger LOG = LoggerFactory.getLogger(GroupMemberDbReader.class);

  private final GroupMapper groupMapper;

  public GroupMemberDbReader(final GroupMapper groupMapper, final RdbmsReaderConfig readerConfig) {
    super(GroupMemberSearchColumn.values(), readerConfig);
    this.groupMapper = groupMapper;
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> search(
      final GroupMemberQuery query, final ResourceAccessChecks resourceAccessChecks) {
    if (shouldReturnEmptyResult(resourceAccessChecks)) {
      return new SearchQueryResult.Builder<GroupMemberEntity>().total(0).items(List.of()).build();
    }

    final var authorizedResourceIds =
        resourceAccessChecks
            .getAuthorizedResourceIdsByType()
            .getOrDefault(AuthorizationResourceType.GROUP.name(), List.of());
    final var dbSort = convertSort(query.sort(), GroupMemberSearchColumn.ENTITY_ID);
    final var dbPage = convertPaging(dbSort, query.page());
    final var dbQuery =
        GroupMemberDbQuery.of(
            b ->
                b.filter(query.filter())
                    .authorizedResourceIds(authorizedResourceIds)
                    .sort(dbSort)
                    .page(dbPage));

    LOG.trace("[RDBMS DB] Search for groups with filter {}", dbQuery);
    final var totalHits = groupMapper.countMembers(dbQuery);

    if (shouldReturnEmptyPage(dbPage, totalHits)) {
      return buildSearchQueryResult(totalHits, List.of(), dbSort);
    }

    final var hits = groupMapper.searchMembers(dbQuery).stream().map(this::map).toList();
    return buildSearchQueryResult(totalHits, hits, dbSort);
  }

  private GroupMemberEntity map(final GroupMemberDbModel model) {
    return new GroupMemberEntity(model.entityId(), EntityType.valueOf(model.entityType()));
  }
}
