/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.hasParentQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.GROUP_ID;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.GroupMemberFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;
import java.util.ArrayList;

public class GroupMemberFilterTransformer extends IndexFilterTransformer<GroupMemberFilter> {
  public GroupMemberFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final GroupMemberFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    if (filter.memberType() != null) {
      queries.add(term(GroupIndex.MEMBER_TYPE, filter.memberType().name()));
    }
    queries.add(
        filter.groupId() == null
            ? term(GroupIndex.JOIN, IdentityJoinRelationshipType.GROUP.getType())
            : hasParentQuery(
                IdentityJoinRelationshipType.GROUP.getType(), term(GROUP_ID, filter.groupId())));

    return and(queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(GROUP_ID, authorization.resourceIds());
  }
}
