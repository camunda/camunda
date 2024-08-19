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
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.DefinitionService;
import java.util.List;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

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

  public QueryBuilder createNestedDocDefinitionQuery(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final DefinitionService definitionService) {
    final BoolQueryBuilder query = boolQuery();
    query.filter(
        DefinitionQueryUtilES.createTenantIdQuery(nestedFieldReference(tenantIdField), tenantIds));
    query.filter(termQuery(nestedFieldReference(definitionKeyField), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      query.filter(
          termsQuery(
              nestedFieldReference(versionField),
              definitionService.getLatestVersionToKey(DefinitionType.PROCESS, definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      query.filter(termsQuery(nestedFieldReference(versionField), definitionVersions));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      query.mustNot(matchAllQuery());
    }
    return query;
  }

  private String nestedFieldReference(final String fieldName) {
    return nestedField + "." + fieldName;
  }
}
