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
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.MEMBER_ID;
import static io.camunda.webapps.schema.descriptors.index.GroupIndex.NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.GroupFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;

public class GroupFilterTransformer extends IndexFilterTransformer<GroupFilter> {
  public GroupFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final GroupFilter filter) {

    return and(
        term(GroupIndex.JOIN, IdentityJoinRelationshipType.GROUP.getType()),
        filter.groupKey() == null ? null : term(KEY, filter.groupKey()),
        filter.name() == null ? null : term(NAME, filter.name()),
        filter.memberIds() == null
            ? null
            : filter.memberIds().isEmpty()
                ? matchNone()
                : hasChildQuery(
                    IdentityJoinRelationshipType.MEMBER.getType(),
                    stringTerms(MEMBER_ID, filter.memberIds())));
  }
}
