/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasParentQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.RoleFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;

public class RoleFilterTransformer extends IndexFilterTransformer<RoleFilter> {
  public RoleFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final RoleFilter filter) {
    if (filter.memberIdsByType() != null && !filter.memberIdsByType().isEmpty()) {
      return createMultipleMemberTypeQuery(filter);
    }

    return and(
        filter.roleId() == null ? null : term(RoleIndex.ROLE_ID, filter.roleId()),
        filter.name() == null ? null : term(RoleIndex.NAME, filter.name()),
        filter.description() == null ? null : term(RoleIndex.DESCRIPTION, filter.description()),
        filter.memberType() == null
            ? null
            : term(RoleIndex.MEMBER_TYPE, filter.memberType().name()),
        filter.joinParentId() == null
            ? term(RoleIndex.JOIN, IdentityJoinRelationshipType.ROLE.getType())
            : hasParentQuery(
                IdentityJoinRelationshipType.ROLE.getType(),
                term(RoleIndex.ROLE_ID, filter.joinParentId())),
        filter.memberIds() == null
            ? null
            : filter.memberIds().isEmpty()
                ? matchNone()
                : hasChildQuery(
                    IdentityJoinRelationshipType.MEMBER.getType(),
                    stringTerms(RoleIndex.MEMBER_ID, filter.memberIds())),
        filter.childMemberType() == null
            ? null
            : hasChildQuery(
                IdentityJoinRelationshipType.MEMBER.getType(),
                term(RoleIndex.MEMBER_TYPE, filter.childMemberType().name())),
        filter.roleIds() == null
            ? null
            : filter.roleIds().isEmpty()
                ? matchNone()
                : stringTerms(RoleIndex.ROLE_ID, filter.roleIds()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(RoleIndex.ROLE_ID, authorization.resourceIds());
  }

  private SearchQuery createMultipleMemberTypeQuery(final RoleFilter filter) {
    return or(
        filter.memberIdsByType().entrySet().stream()
            .map(
                entry ->
                    hasChildQuery(
                        IdentityJoinRelationshipType.MEMBER.getType(),
                        and(
                            term(RoleIndex.MEMBER_TYPE, entry.getKey().name()),
                            stringTerms(RoleIndex.MEMBER_ID, entry.getValue()))))
            .toList());
  }
}
