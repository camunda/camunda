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
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.GroupFilter;
import io.camunda.webapps.schema.descriptors.usermanagement.index.GroupIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;
import java.util.List;

public class GroupFilterTransformer implements FilterTransformer<GroupFilter> {

  @Override
  public SearchQuery toSearchQuery(final GroupFilter filter) {

    return and(
        term(GroupIndex.JOIN, IdentityJoinRelationshipType.GROUP.getType()),
        filter.groupKey() == null ? null : term(GroupIndex.KEY, filter.groupKey()),
        filter.name() == null ? null : term(GroupIndex.NAME, filter.name()),
        filter.memberKey() == null
            ? null
            : hasChildQuery(
                IdentityJoinRelationshipType.MEMBER.getType(),
                term(GroupIndex.MEMBER_KEY, filter.memberKey())));
  }

  @Override
  public List<String> toIndices(final GroupFilter filter) {
    return List.of("camunda-group-8.7.0_alias");
  }
}
