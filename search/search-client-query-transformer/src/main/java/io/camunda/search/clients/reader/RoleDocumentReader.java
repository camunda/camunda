/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class RoleDocumentReader extends DocumentBasedReader implements RoleReader {

  private final TenantMemberDocumentReader tenantMemberReader;

  public RoleDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final IndexDescriptor indexDescriptor,
      final TenantMemberDocumentReader tenantMemberReader) {
    super(executor, indexDescriptor);
    this.tenantMemberReader = tenantMemberReader;
  }

  @Override
  public RoleEntity getById(final String id, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            RoleQuery.of(b -> b.filter(f -> f.roleId(id)).singleResult()),
            io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class);
  }

  @Override
  public SearchQueryResult<RoleEntity> search(
      final RoleQuery query, final ResourceAccessChecks resourceAccessChecks) {
    var roleQuery = query;
    if (query.filter().tenantId() != null) {
      roleQuery = expandTenantFilter(query);
    }

    return getSearchExecutor()
        .search(
            roleQuery,
            io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class,
            resourceAccessChecks);
  }

  private RoleQuery expandTenantFilter(final RoleQuery query) {
    final var roleIds = tenantMemberReader.getTenantMembers(query.filter().tenantId(), ROLE);
    return query.toBuilder().filter(query.filter().toBuilder().roleIds(roleIds).build()).build();
  }
}
