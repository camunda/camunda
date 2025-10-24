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

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.RoleMemberFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;

public class RoleMemberFilterTransformer extends IndexFilterTransformer<RoleMemberFilter> {
  public RoleMemberFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final RoleMemberFilter filter) {
    return and(
        filter.memberType() == null
            ? null
            : term(RoleIndex.MEMBER_TYPE, filter.memberType().name()),
        filter.roleId() == null
            ? term(RoleIndex.JOIN, IdentityJoinRelationshipType.ROLE.getType())
            : hasParentQuery(
                IdentityJoinRelationshipType.ROLE.getType(),
                term(RoleIndex.ROLE_ID, filter.roleId())));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(RoleIndex.ROLE_ID, authorization.resourceIds());
  }
}
