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
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.GROUP_ID;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.MEMBER_ID;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.GroupFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;
import java.util.ArrayList;
import java.util.List;

public class GroupFilterTransformer extends IndexFilterTransformer<GroupFilter> {
  public GroupFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public GroupFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessResult resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public GroupFilterTransformer withResourceAccessFilter(
      final ResourceAccessResult resourceAccessResult) {
    return new GroupFilterTransformer(indexDescriptor, resourceAccessResult);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    final var resourceIds = authorization.resourceIds();
    return stringTerms(GROUP_ID, resourceIds);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    return matchAll();
  }

  @Override
  public SearchQuery toSearchQuery(final GroupFilter filter) {
    if (filter.memberIdsByType() != null && !filter.memberIdsByType().isEmpty()) {
      return createMultipleMemberTypeQuery(filter);
    }
    final var queries = new ArrayList<SearchQuery>();
    if (filter.groupKey() != null) {
      queries.add(term(KEY, filter.groupKey()));
    }
    if (filter.groupIdOperations() != null && !filter.groupIdOperations().isEmpty()) {
      queries.addAll(stringOperations(GROUP_ID, filter.groupIdOperations()));
    }
    if (filter.name() != null) {
      queries.add(term(NAME, filter.name()));
    }
    if (filter.description() != null) {
      queries.add(term(GroupIndex.DESCRIPTION, filter.description()));
    }
    if (filter.memberIds() != null) {
      queries.add(
          filter.memberIds().isEmpty()
              ? matchNone()
              : hasChildQuery(
                  IdentityJoinRelationshipType.MEMBER.getType(),
                  stringTerms(MEMBER_ID, filter.memberIds())));
    }
    if (filter.memberType() != null) {
      queries.add(term(GroupIndex.MEMBER_TYPE, filter.memberType().name()));
    }
    queries.add(
        filter.joinParentId() == null
            ? term(GroupIndex.JOIN, IdentityJoinRelationshipType.GROUP.getType())
            : hasParentQuery(
                IdentityJoinRelationshipType.GROUP.getType(),
                term(GROUP_ID, filter.joinParentId())));
    if (filter.childMemberType() != null) {
      queries.add(
          hasChildQuery(
              IdentityJoinRelationshipType.MEMBER.getType(),
              term(GroupIndex.MEMBER_TYPE, filter.childMemberType().name())));
    }

    return and(queries);
  }

  private SearchQuery createMultipleMemberTypeQuery(final GroupFilter filter) {
    return or(
        filter.memberIdsByType().entrySet().stream()
            .map(
                entry ->
                    hasChildQuery(
                        IdentityJoinRelationshipType.MEMBER.getType(),
                        and(
                            term(GroupIndex.MEMBER_TYPE, entry.getKey().name()),
                            stringTerms(GroupIndex.MEMBER_ID, entry.getValue()))))
            .toList());
  }
}
