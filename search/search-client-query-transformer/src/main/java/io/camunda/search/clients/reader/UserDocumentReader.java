/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.zeebe.protocol.record.value.EntityType.USER;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class UserDocumentReader extends DocumentBasedReader implements UserReader {

  private final RoleMemberDocumentReader roleMemberReader;
  private final TenantMemberDocumentReader tenantMemberReader;
  private final GroupMemberDocumentReader groupMemberReader;

  public UserDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final RoleMemberDocumentReader roleMemberReader,
      final TenantMemberDocumentReader tenantMemberReader,
      final GroupMemberDocumentReader groupMemberReader) {
    super(executor, indexDescriptor);
    this.roleMemberReader = roleMemberReader;
    this.tenantMemberReader = tenantMemberReader;
    this.groupMemberReader = groupMemberReader;
  }

  @Override
  public UserEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            id,
            io.camunda.webapps.schema.entities.usermanagement.UserEntity.class,
            indexDescriptor.getFullQualifiedName());
  }

  @Override
  public SearchQueryResult<UserEntity> search(
      final UserQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            applyFilters(query),
            io.camunda.webapps.schema.entities.usermanagement.UserEntity.class,
            resourceAccessChecks);
  }

  private UserQuery applyFilters(final UserQuery userQuery) {
    if (userQuery.filter().tenantId() != null) {
      return expandTenantFilter(userQuery);
    }
    if (userQuery.filter().groupId() != null) {
      return expandGroupFilter(userQuery);
    }
    if (userQuery.filter().roleId() != null) {
      return expandRoleFilter(userQuery);
    }
    return userQuery;
  }

  private UserQuery expandTenantFilter(final UserQuery userQuery) {
    final var usernames = tenantMemberReader.getTenantMembers(userQuery.filter().tenantId(), USER);
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }

  private UserQuery expandGroupFilter(final UserQuery userQuery) {
    final var usernames = groupMemberReader.getGroupMembers(userQuery.filter().groupId(), USER);
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }

  private UserQuery expandRoleFilter(final UserQuery userQuery) {
    final var usernames = roleMemberReader.getRoleMembers(userQuery.filter().roleId(), USER);
    return userQuery.toBuilder()
        .filter(userQuery.filter().toBuilder().usernames(usernames).build())
        .build();
  }
}
