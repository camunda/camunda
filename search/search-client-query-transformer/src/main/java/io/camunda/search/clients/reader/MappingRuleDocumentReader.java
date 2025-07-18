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

public class MappingRuleDocumentReader extends DocumentBasedReader implements MappingRuleReader {

  private final RoleMemberDocumentReader roleMemberReader;
  private final TenantMemberDocumentReader tenantMemberReader;
  private final GroupMemberDocumentReader groupMemberReader;

  public MappingRuleDocumentReader(
      final SearchClientBasedQueryExecutor executor,
      final RoleMemberDocumentReader roleMemberReader,
      final TenantMemberDocumentReader tenantMemberReader,
      final GroupMemberDocumentReader groupMemberReader) {
    super(executor);
    this.roleMemberReader = roleMemberReader;
    this.tenantMemberReader = tenantMemberReader;
    this.groupMemberReader = groupMemberReader;
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

  private MappingRuleQuery applyFilters(final MappingRuleQuery mappingQuery) {
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

  private MappingRuleQuery expandTenantFilter(final MappingRuleQuery query) {
    final var mappingIds =
        tenantMemberReader.getTenantMembers(query.filter().tenantId(), MAPPING_RULE);
    return query.toBuilder()
        .filter(query.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private MappingRuleQuery expandGroupFilter(final MappingRuleQuery mappingQuery) {
    final var mappingIds =
        groupMemberReader.getGroupMembers(mappingQuery.filter().groupId(), MAPPING_RULE);
    return mappingQuery.toBuilder()
        .filter(mappingQuery.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }

  private MappingRuleQuery expandRoleFilter(final MappingRuleQuery query) {
    final var mappingIds = roleMemberReader.getRoleMembers(query.filter().roleId(), MAPPING_RULE);
    return query.toBuilder()
        .filter(query.filter().toBuilder().mappingIds(mappingIds).build())
        .build();
  }
}
