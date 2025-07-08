/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.zeebe.protocol.record.value.EntityType.ROLE;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class RoleDocumentReader extends DocumentBasedReader implements RoleReader {

  private final TenantMemberDocumentReader tenantMemberReader;

  public RoleDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor,
      final TenantMemberDocumentReader tenantMemberReader) {
    super(searchClient, transformers, indexDescriptor);
    this.tenantMemberReader = tenantMemberReader;
  }

  @Override
  public RoleEntity getByKey(final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getByQuery(
            RoleQuery.of(b -> b.filter(f -> f.roleId(key)).singleResult()),
            io.camunda.webapps.schema.entities.usermanagement.RoleEntity.class,
            resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<RoleEntity> search(
      final RoleQuery query, final ResourceAccessChecks resourceAccessChecks) {
    var roleQuery = query;
    if (roleQuery.filter().tenantId() != null) {
      roleQuery = expandTenantFilter(roleQuery);
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
