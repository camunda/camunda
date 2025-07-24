/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.zeebe.protocol.record.value.EntityType.GROUP;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class GroupDocumentReader extends DocumentBasedReader implements GroupReader {

  private final TenantMemberDocumentReader tenantMemberReader;
  private final RoleMemberDocumentReader roleMemberReader;

  public GroupDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final TenantMemberDocumentReader tenantMemberReader,
      final RoleMemberDocumentReader roleMemberReader) {
    super(executor, indexDescriptor);
    this.tenantMemberReader = tenantMemberReader;
    this.roleMemberReader = roleMemberReader;
  }

  @Override
  public GroupEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            GroupQuery.of(b -> b.filter(f -> f.groupIds(id)).singleResult()),
            io.camunda.webapps.schema.entities.usermanagement.GroupEntity.class);
  }

  @Override
  public SearchQueryResult<GroupEntity> search(
      final GroupQuery query, final ResourceAccessChecks resourceAccessChecks) {
    var groupQuery = query;

    if (query.filter().tenantId() != null) {
      groupQuery = expandTenantFilter(query);
    }
    if (query.filter().roleId() != null) {
      groupQuery = expandRoleFilter(query);
    }

    return getSearchExecutor()
        .search(
            groupQuery,
            io.camunda.webapps.schema.entities.usermanagement.GroupEntity.class,
            resourceAccessChecks);
  }

  private GroupQuery expandTenantFilter(final GroupQuery query) {
    final var groupIds = tenantMemberReader.getTenantMembers(query.filter().tenantId(), GROUP);
    return query.toBuilder().filter(query.filter().toBuilder().groupIds(groupIds).build()).build();
  }

  private GroupQuery expandRoleFilter(final GroupQuery query) {
    final var groupIds = roleMemberReader.getRoleMembers(query.filter().roleId(), GROUP);
    return query.toBuilder().filter(query.filter().toBuilder().groupIds(groupIds).build()).build();
  }
}
