/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.service.db.os.client.dsl.QueryDSL;
import io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class DefinitionQueryUtilOS {

  private DefinitionQueryUtilOS() {}

  public static Query createDefinitionQuery(
      final String definitionKey, final List<String> tenantIds, final AbstractInstanceIndex type) {
    return createDefinitionQuery(
        definitionKey,
        ImmutableList.of(ReportConstants.ALL_VERSIONS),
        tenantIds,
        type,
        // not relevant
        s -> "");
  }

  public static Query createDefinitionQuery(
      final Map<String, Set<String>> definitionKeyToTenantsMap,
      final String definitionKeyFieldName,
      final String tenantKeyFieldName) {
    final BoolQuery.Builder query = new BoolQuery.Builder().minimumShouldMatch("1");
    definitionKeyToTenantsMap.forEach(
        (definitionKey, tenantIds) ->
            query.should(
                new BoolQuery.Builder()
                    .must(QueryDSL.term(definitionKeyFieldName, definitionKey))
                    .must(createTenantIdQuery(tenantKeyFieldName, new ArrayList<>(tenantIds)))
                    .build()
                    .toQuery()));
    return query.build().toQuery();
  }

  public static Query createDefinitionQuery(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final AbstractInstanceIndex type,
      final UnaryOperator<String> getLatestVersionToKey) {
    final BoolQuery.Builder query = new BoolQuery.Builder();
    query.must(createTenantIdQuery(type.getTenantIdFieldName(), tenantIds));
    query.must(QueryDSL.term(type.getDefinitionKeyFieldName(), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      query.must(
          QueryDSL.term(
              type.getDefinitionVersionFieldName(), getLatestVersionToKey.apply(definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      query.must(QueryDSL.stringTerms(type.getDefinitionVersionFieldName(), definitionVersions));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      query.mustNot(QueryDSL.matchAll());
    }
    return query.build().toQuery();
  }

  public static Query createTenantIdQuery(final String tenantField, final List<String> tenantIds) {
    final AtomicBoolean includeNotDefinedTenant = new AtomicBoolean(false);
    final List<String> tenantIdTerms =
        tenantIds.stream()
            .peek(
                id -> {
                  if (id == null) {
                    includeNotDefinedTenant.set(true);
                  }
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    final BoolQuery.Builder tenantQueryBuilder = new BoolQuery.Builder().minimumShouldMatch("1");
    if (!tenantIdTerms.isEmpty()) {
      tenantQueryBuilder.should(QueryDSL.stringTerms(tenantField, tenantIdTerms));
    }
    if (includeNotDefinedTenant.get()) {
      tenantQueryBuilder.should(
          new BoolQuery.Builder().mustNot(QueryDSL.exists(tenantField)).build().toQuery());
    }
    if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant.get()) {
      // All tenants have been deselected and therefore we should not return any data.
      // This query ensures that the condition never holds for any data.
      tenantQueryBuilder.mustNot(QueryDSL.matchAll());
    }

    return tenantQueryBuilder.build().toQuery();
  }
}
