/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.util;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;
import static java.util.stream.Collectors.groupingBy;

import io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class DefinitionQueryUtilOS {

  private DefinitionQueryUtilOS() {}

  public static BoolQuery.Builder createDefinitionQuery(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final AbstractInstanceIndex type,
      final UnaryOperator<String> getLatestVersionToKey) {
    final BoolQuery.Builder queryBuilder = new BoolQuery.Builder();
    queryBuilder.must(createTenantIdQuery(type.getTenantIdFieldName(), tenantIds));
    queryBuilder.must(term(type.getDefinitionKeyFieldName(), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      queryBuilder.must(
          term(type.getDefinitionVersionFieldName(), getLatestVersionToKey.apply(definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      queryBuilder.must(stringTerms(type.getDefinitionVersionFieldName(), definitionVersions));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      queryBuilder.mustNot(matchAll());
    }
    return queryBuilder;
  }

  public static Query createTenantIdQuery(final String tenantField, final List<String> tenantIds) {
    final Map<Boolean, List<String>> groupedByNullTenantIds =
        tenantIds.stream().collect(groupingBy(Objects::isNull));
    final boolean includeNotDefinedTenant = groupedByNullTenantIds.containsKey(true);
    final List<String> tenantIdTerms = groupedByNullTenantIds.getOrDefault(false, List.of());

    final BoolQuery.Builder tenantQueryBuilder = new BoolQuery.Builder().minimumShouldMatch("1");
    if (!tenantIdTerms.isEmpty()) {
      tenantQueryBuilder.should(stringTerms(tenantField, tenantIdTerms));
    }
    if (includeNotDefinedTenant) {
      tenantQueryBuilder.should(not(exists(tenantField)));
    }
    if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant) {
      // All tenants have been deselected and therefore we should not return any data.
      // This query ensures that the condition never holds for any data.
      tenantQueryBuilder.mustNot(matchAll());
    }

    return tenantQueryBuilder.build().toQuery();
  }
}
