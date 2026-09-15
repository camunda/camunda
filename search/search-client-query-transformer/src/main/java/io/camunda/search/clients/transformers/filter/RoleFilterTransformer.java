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
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.RoleFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;
import java.util.ArrayList;

public class RoleFilterTransformer extends IndexFilterTransformer<RoleFilter> {
  public RoleFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final RoleFilter filter) {
    final var queries = new ArrayList<>(toSearchQueryFields(filter));
    queries.add(term(RoleIndex.JOIN, IdentityJoinRelationshipType.ROLE.getType()));

    if (filter.memberIdsByType() != null && !filter.memberIdsByType().isEmpty()) {
      queries.add(createMultipleMemberTypeQuery(filter));
    }

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      queries.add(or(filter.orFilters().stream().map(f -> and(toSearchQueryFields(f))).toList()));
    }

    return and(queries);
  }

  private ArrayList<SearchQuery> toSearchQueryFields(final RoleFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    if (filter.roleIdOperations() != null && !filter.roleIdOperations().isEmpty()) {
      queries.addAll(stringOperations(RoleIndex.ROLE_ID, filter.roleIdOperations()));
    }
    if (filter.nameOperations() != null && !filter.nameOperations().isEmpty()) {
      queries.addAll(stringOperations(RoleIndex.NAME, filter.nameOperations()));
    }
    if (filter.description() != null) {
      queries.add(term(RoleIndex.DESCRIPTION, filter.description()));
    }
    if (filter.memberIds() != null) {
      queries.add(
          filter.memberIds().isEmpty()
              ? matchNone()
              : hasChildQuery(
                  IdentityJoinRelationshipType.MEMBER.getType(),
                  stringTerms(RoleIndex.MEMBER_ID, filter.memberIds())));
    }
    if (filter.childMemberType() != null) {
      queries.add(
          hasChildQuery(
              IdentityJoinRelationshipType.MEMBER.getType(),
              term(RoleIndex.MEMBER_TYPE, filter.childMemberType().name())));
    }
    if (filter.roleIds() != null) {
      queries.add(
          filter.roleIds().isEmpty()
              ? matchNone()
              : stringTerms(RoleIndex.ROLE_ID, filter.roleIds()));
    }
    return queries;
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
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
