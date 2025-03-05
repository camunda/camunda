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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex.KEY;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex.NAME;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex.TENANT_ID;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.TenantFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.usermanagement.index.TenantIndex;
import io.camunda.webapps.schema.entities.usermanagement.EntityJoinRelation.IdentityJoinRelationshipType;

public class TenantFilterTransformer extends IndexFilterTransformer<TenantFilter> {

  public TenantFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final TenantFilter filter) {
    return and(
        filter.key() == null ? null : term(KEY, filter.key()),
        filter.tenantId() == null ? null : term(TENANT_ID, filter.tenantId()),
        filter.name() == null ? null : term(NAME, filter.name()),
        filter.memberIds() == null
            ? null
            : filter.memberIds().isEmpty()
                ? matchNone()
                : hasChildQuery(
                    IdentityJoinRelationshipType.MEMBER.getType(),
                    stringTerms(TenantIndex.MEMBER_ID, filter.memberIds())),
        filter.entityType() == null
            ? null
            : term(TenantIndex.MEMBER_TYPE, filter.entityType().name()),
        filter.joinParentId() == null
            ? term(TenantIndex.JOIN, IdentityJoinRelationshipType.TENANT.getType())
            : hasParentQuery(
                IdentityJoinRelationshipType.TENANT.getType(),
                term(TENANT_ID, filter.joinParentId())));
  }
}
