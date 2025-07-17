/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleMemberDocumentReader extends DocumentBasedReader implements RoleMemberReader {

  public RoleMemberDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<RoleMemberEntity> search(
      final RoleQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity.class,
            resourceAccessChecks);
  }

  public Set<String> getRoleMembers(final String roleId, final EntityType memberType) {
    final var roleMemberQuery =
        RoleQuery.of(b -> b.filter(f -> f.joinParentId(roleId).memberType(memberType)).unlimited());
    return search(roleMemberQuery, ResourceAccessChecks.disabled()).items().stream()
        .map(RoleMemberEntity::id)
        .collect(Collectors.toSet());
  }
}
