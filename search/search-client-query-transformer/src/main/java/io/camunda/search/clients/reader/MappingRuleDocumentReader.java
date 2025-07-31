/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.zeebe.protocol.record.value.EntityType.MAPPING_RULE;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class MappingRuleDocumentReader extends DocumentBasedReader implements MappingRuleReader {

  private final RoleMemberDocumentReader roleMemberReader;
  private final TenantMemberDocumentReader tenantMemberReader;
  private final GroupMemberDocumentReader groupMemberReader;

  public MappingRuleDocumentReader(
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
  public MappingRuleEntity getById(
      final String id, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .getById(
            id,
            io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity.class,
            indexDescriptor.getFullQualifiedName());
  }

  @Override
  public SearchQueryResult<MappingRuleEntity> search(
      final MappingRuleQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            applyFilters(query),
            io.camunda.webapps.schema.entities.usermanagement.MappingRuleEntity.class,
            resourceAccessChecks);
  }

  private MappingRuleQuery applyFilters(final MappingRuleQuery mappingRuleQuery) {
    if (mappingRuleQuery.filter().tenantId() != null) {
      return expandTenantFilter(mappingRuleQuery);
    }
    if (mappingRuleQuery.filter().groupId() != null) {
      return expandGroupFilter(mappingRuleQuery);
    }
    if (mappingRuleQuery.filter().roleId() != null) {
      return expandRoleFilter(mappingRuleQuery);
    }
    return mappingRuleQuery;
  }

  private MappingRuleQuery expandTenantFilter(final MappingRuleQuery query) {
    final var mappingRuleIds =
        tenantMemberReader.getTenantMembers(query.filter().tenantId(), MAPPING_RULE);
    return query.toBuilder()
        .filter(query.filter().toBuilder().mappingRuleIds(mappingRuleIds).build())
        .build();
  }

  private MappingRuleQuery expandGroupFilter(final MappingRuleQuery mappingRuleQuery) {
    final var mappingRuleIds =
        groupMemberReader.getGroupMembers(mappingRuleQuery.filter().groupId(), MAPPING_RULE);
    return mappingRuleQuery.toBuilder()
        .filter(mappingRuleQuery.filter().toBuilder().mappingRuleIds(mappingRuleIds).build())
        .build();
  }

  private MappingRuleQuery expandRoleFilter(final MappingRuleQuery query) {
    final var mappingRuleIds =
        roleMemberReader.getRoleMembers(query.filter().roleId(), MAPPING_RULE);
    return query.toBuilder()
        .filter(query.filter().toBuilder().mappingRuleIds(mappingRuleIds).build())
        .build();
  }
}
