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
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.DefinitionService;
import java.util.List;

public class NestedDefinitionQueryBuilder {

  private final String nestedField;
  private final String definitionKeyField;
  private final String versionField;
  private final String tenantIdField;

  public NestedDefinitionQueryBuilder(
      final String nestedField,
      final String definitionKeyField,
      final String versionField,
      final String tenantIdField) {
    this.nestedField = nestedField;
    this.definitionKeyField = definitionKeyField;
    this.versionField = versionField;
    this.tenantIdField = tenantIdField;
  }

  public Query.Builder createNestedDocDefinitionQueryBuilder(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final DefinitionService definitionService) {
    final Query.Builder builder = new Query.Builder();
    final BoolQuery.Builder queryBuilder = new BoolQuery.Builder();
    queryBuilder.filter(
        DefinitionQueryUtilES.createTenantIdQuery(nestedFieldReference(tenantIdField), tenantIds));
    queryBuilder.filter(
        f -> f.term(t -> t.field(nestedFieldReference(definitionKeyField)).value(definitionKey)));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      queryBuilder.filter(
          f ->
              f.terms(
                  t ->
                      t.field(nestedFieldReference(versionField))
                          .terms(
                              tt ->
                                  tt.value(
                                      List.of(
                                          FieldValue.of(
                                              definitionService.getLatestVersionToKey(
                                                  DefinitionType.PROCESS, definitionKey)))))));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      queryBuilder.filter(
          f ->
              f.terms(
                  t ->
                      t.field(nestedFieldReference(versionField))
                          .terms(
                              tt ->
                                  tt.value(
                                      definitionVersions.stream().map(FieldValue::of).toList()))));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      queryBuilder.mustNot(m -> m.matchAll(r -> r));
    }
    builder.bool(queryBuilder.build());
    return builder;
  }

  private String nestedFieldReference(final String fieldName) {
    return nestedField + "." + fieldName;
  }
}
