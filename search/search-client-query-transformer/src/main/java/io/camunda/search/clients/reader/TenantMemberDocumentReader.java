/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.TenantMemberEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TenantQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.Set;
import java.util.stream.Collectors;

public class TenantMemberDocumentReader extends DocumentBasedReader implements TenantMemberReader {

  public TenantMemberDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<TenantMemberEntity> search(
      final TenantQuery query, final ResourceAccessChecks resourceAccessChecks) {
    return getSearchExecutor()
        .search(
            query,
            io.camunda.webapps.schema.entities.usermanagement.TenantMemberEntity.class,
            resourceAccessChecks);
  }

  public Set<String> getTenantMembers(final String tenantId, final EntityType entityType) {
    final var tenantMemberQuery =
        TenantQuery.of(
            b -> b.filter(f -> f.joinParentId(tenantId).memberType(entityType)).unlimited());
    return search(tenantMemberQuery, ResourceAccessChecks.disabled()).items().stream()
        .map(TenantMemberEntity::id)
        .collect(Collectors.toSet());
  }
}
