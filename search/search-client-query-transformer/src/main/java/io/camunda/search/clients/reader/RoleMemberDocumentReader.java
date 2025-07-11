/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.RoleMemberEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import java.util.stream.Collectors;

public class RoleMemberDocumentReader extends DocumentBasedReader implements RoleMemberReader {

  public RoleMemberDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor) {
    super(searchClient, transformers, indexDescriptor);
  }

  @Override
  public RoleMemberEntity getByKey(
      final String key, final ResourceAccessChecks resourceAccessChecks) {
    throw new UnsupportedOperationException("RoleMemberReader#getByKey not supported");
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
    final var tenantMemberQuery =
        RoleQuery.of(b -> b.filter(f -> f.joinParentId(roleId).memberType(memberType)).unlimited());
    return search(tenantMemberQuery, ResourceAccessChecks.disabled()).items().stream()
        .map(RoleMemberEntity::id)
        .collect(Collectors.toSet());
  }
}
