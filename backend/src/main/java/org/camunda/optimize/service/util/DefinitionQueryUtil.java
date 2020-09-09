/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.es.schema.index.DefinitionBasedType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DefinitionQueryUtil {

  public static BoolQueryBuilder createDefinitionQuery(String definitionKey,
                                                       List<String> definitionVersions,
                                                       List<String> tenantIds,
                                                       final DefinitionBasedType type,
                                                       Function<String, String> getLatestVersionToKey) {
    final BoolQueryBuilder query = boolQuery();
    query.must(createTenantIdQuery(type.getTenantIdFieldName(), tenantIds));
    query.must(termQuery(type.getDefinitionKeyFieldName(), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      query.must(termsQuery(type.getDefinitionVersionFieldName(), getLatestVersionToKey.apply(definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      query.must(termsQuery(type.getDefinitionVersionFieldName(), definitionVersions));
    } else if (definitionVersions.isEmpty()) {
      query.mustNot(existsQuery(type.getDefinitionVersionFieldName()));
    }
    return query;
  }

  private static QueryBuilder createTenantIdQuery(final String tenantField, final List<String> tenantIds) {
    final AtomicBoolean includeNotDefinedTenant = new AtomicBoolean(false);
    final List<String> tenantIdTerms = tenantIds.stream()
      .peek(id -> {
        if (id == null) {
          includeNotDefinedTenant.set(true);
        }
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    final BoolQueryBuilder tenantQueryBuilder = boolQuery().minimumShouldMatch(1);
    if (!tenantIdTerms.isEmpty()) {
      tenantQueryBuilder.should(termsQuery(tenantField, tenantIdTerms));
    }
    if (includeNotDefinedTenant.get()) {
      tenantQueryBuilder.should(boolQuery().mustNot(existsQuery(tenantField)));
    }
    if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant.get()) {
      // All tenants have been deselected and therefore we should not return any data.
      // This query ensures that the condition never holds for any data.
      tenantQueryBuilder.mustNot(matchAllQuery());
    }

    return tenantQueryBuilder;
  }

}
