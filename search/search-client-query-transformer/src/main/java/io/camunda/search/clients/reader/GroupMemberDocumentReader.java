/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import java.util.stream.Collectors;

public class GroupMemberDocumentReader extends DocumentBasedReader implements GroupMemberReader {

  public GroupMemberDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<GroupMemberEntity> search(
      final GroupQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.usermanagement.GroupMemberEntity.class,
            resourceAccessChecks);
  }

  public Set<String> getGroupMembers(final String groupId, final EntityType entityType) {
    final var groupMemberQuery =
        GroupQuery.of(
            b -> b.filter(f -> f.joinParentId(groupId).memberType(entityType)).unlimited());
    return search(groupMemberQuery, ResourceAccessChecks.disabled()).items().stream()
        .map(GroupMemberEntity::id)
        .collect(Collectors.toSet());
  }
}
