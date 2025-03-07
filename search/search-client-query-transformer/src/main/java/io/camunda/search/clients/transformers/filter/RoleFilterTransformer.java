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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.RoleFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.usermanagement.index.RoleIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;

public class RoleFilterTransformer extends IndexFilterTransformer<RoleFilter> {
  public RoleFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final RoleFilter filter) {
    return and(
        term(RoleIndex.JOIN, IdentityJoinRelationshipType.ROLE.getType()),
        filter.roleKey() == null ? null : term(RoleIndex.KEY, filter.roleKey()),
        filter.name() == null ? null : term(RoleIndex.NAME, filter.name()),
        filter.memberIds() == null
            ? null
            : filter.memberIds().isEmpty()
                ? matchNone()
                : hasChildQuery(
                    IdentityJoinRelationshipType.MEMBER.getType(),
                    stringTerms(RoleIndex.MEMBER_KEY, filter.memberIds())));
  }
}
