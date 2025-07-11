/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class MappingDocumentReader extends DocumentBasedReader implements MappingReader {

  private final RoleMemberDocumentReader roleMemberReader;
  private final TenantMemberDocumentReader tenantMemberReader;
  private final GroupMemberDocumentReader groupMemberReader;

  public MappingDocumentReader(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptor indexDescriptor,
      final RoleMemberDocumentReader roleMemberReader,
      final TenantMemberDocumentReader tenantMemberReader,
      final GroupMemberDocumentReader groupMemberReader) {
    super(searchClient, transformers, indexDescriptor);
    this.roleMemberReader = roleMemberReader;
    this.tenantMemberReader = tenantMemberReader;
    this.groupMemberReader = groupMemberReader;
  }

  @Override
  public MappingEntity getByKey(final String key, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            key,
            io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class,
            indexDescriptor.getFullQualifiedName(),
            resourceAccessChecks);
  }

  @Override
  public SearchQueryResult<MappingEntity> search(
      final MappingQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            applyFilters(query),
            io.camunda.webapps.schema.entities.usermanagement.MappingEntity.class,
            resourceAccessChecks);
  }

  private MappingQuery applyFilters(final MappingQuery mappingQuery) {
    if (mappingQuery.filter().tenantId() != null) {
      return expandTenantFilter(mappingQuery);
    }
    if (mappingQuery.filter().groupId() != null) {
      return expandGroupFilter(mappingQuery);
    }
    if (mappingQuery.filter().roleId() != null) {
      return expandRoleFilter(mappingQuery);
    }
    return mappingQuery;
  }

  private MappingQuery expandTenantFilter(final MappingQuery query) {
    final var mappingIds = tenantMemberReader.getTenantMembers(query.filter().tenantId(), MAPPING);
    return query.toBuilder()
        .filter(query.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private MappingQuery expandGroupFilter(final MappingQuery mappingQuery) {
    final var mappingIds =
        groupMemberReader.getGroupMembers(mappingQuery.filter().groupId(), MAPPING);
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private MappingQuery expandRoleFilter(final MappingQuery query) {
    final var mappingIds = roleMemberReader.getRoleMembers(query.filter().roleId(), MAPPING);
    return query.toBuilder()
        .filter(query.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }
}
