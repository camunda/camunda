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

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

public final class DefinitionQueryUtilES {

  private DefinitionQueryUtilES() {}

  public static BoolQuery createDefinitionQuery(
      final String definitionKey, final List<String> tenantIds, final AbstractInstanceIndex type) {
    return createDefinitionQuery(
            definitionKey,
            ImmutableList.of(ReportConstants.ALL_VERSIONS),
            tenantIds,
            type,
            // not relevant
            s -> "")
        .build();
  }

  public static Query createDefinitionQuery(
      final Map<String, Set<String>> definitionKeyToTenantsMap,
      final String definitionKeyFieldName,
      final String tenantKeyFieldName) {
    return Query.of(
        bb ->
            bb.bool(
                b -> {
                  b.minimumShouldMatch("1");
                  definitionKeyToTenantsMap.forEach(
                      (definitionKey, tenantIds) ->
                          b.should(
                              s ->
                                  s.bool(
                                      bol ->
                                          bol.must(
                                                  m ->
                                                      m.term(
                                                          t ->
                                                              t.field(definitionKeyFieldName)
                                                                  .value(
                                                                      FieldValue.of(
                                                                          definitionKey))))
                                              .must(
                                                  createTenantIdQuery(
                                                      tenantKeyFieldName,
                                                      new ArrayList<>(tenantIds))))));

                  return b;
                }));
  }

  public static BoolQuery.Builder createDefinitionQuery(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final AbstractInstanceIndex<?> type,
      final UnaryOperator<String> getLatestVersionToKey) {
    final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
    boolQueryBuilder
        .must(
            m ->
                m.term(
                    t ->
                        t.field(type.getDefinitionKeyFieldName())
                            .value(FieldValue.of(definitionKey))))
        .must(createTenantIdQuery(type.getTenantIdFieldName(), tenantIds));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      boolQueryBuilder.must(
          m ->
              m.terms(
                  t ->
                      t.field(type.getDefinitionVersionFieldName())
                          .terms(
                              tt ->
                                  tt.value(
                                      List.of(
                                          FieldValue.of(
                                              getLatestVersionToKey.apply(definitionKey)))))));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      boolQueryBuilder.must(
          m ->
              m.terms(
                  t ->
                      t.field(type.getDefinitionVersionFieldName())
                          .terms(
                              tt ->
                                  tt.value(
                                      definitionVersions.stream().map(FieldValue::of).toList()))));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      boolQueryBuilder.mustNot(m -> m.matchAll(a -> a));
    }
    return boolQueryBuilder;
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
            .toList();

    final BoolQuery tenantQuery =
        BoolQuery.of(
            b -> {
              b.minimumShouldMatch("1");
              if (!tenantIdTerms.isEmpty()) {
                b.should(
                    s ->
                        s.terms(
                            t ->
                                t.field(tenantField)
                                    .terms(
                                        tt ->
                                            tt.value(
                                                tenantIdTerms.stream()
                                                    .map(FieldValue::of)
                                                    .toList()))));
              }
              if (includeNotDefinedTenant.get()) {
                b.should(s -> s.bool(bb -> bb.mustNot(m -> m.exists(e -> e.field(tenantField)))));
              }
              if (tenantIdTerms.isEmpty() && !includeNotDefinedTenant.get()) {
                // All tenants have been deselected and therefore we should not return any data.
                // This query ensures that the condition never holds for any data.
                b.mustNot(m -> m.matchAll(a -> a));
              }
              return b;
            });

    return Query.of(b -> b.bool(tenantQuery));
  }
}
