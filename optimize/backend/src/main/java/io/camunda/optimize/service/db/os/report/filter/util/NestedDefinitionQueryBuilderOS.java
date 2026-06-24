/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter.util;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static io.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.util.DefinitionQueryUtilOS;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;

public class NestedDefinitionQueryBuilderOS {

  private final String nestedField;
  private final String definitionKeyField;
  private final String versionField;
  private final String tenantIdField;

  public NestedDefinitionQueryBuilderOS(
      final String nestedField,
      final String definitionKeyField,
      final String versionField,
      final String tenantIdField) {
    this.nestedField = nestedField;
    this.definitionKeyField = definitionKeyField;
    this.versionField = versionField;
    this.tenantIdField = tenantIdField;
  }

  public BoolQuery.Builder createNestedDocDefinitionQuery(
      final String definitionKey,
      final List<String> definitionVersions,
      final List<String> tenantIds,
      final DefinitionService definitionService) {
    final BoolQuery.Builder queryBuilder = new BoolQuery.Builder();
    queryBuilder.filter(
        DefinitionQueryUtilOS.createTenantIdQuery(nestedFieldReference(tenantIdField), tenantIds));
    queryBuilder.filter(term(nestedFieldReference(definitionKeyField), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      queryBuilder.filter(
          term(
              nestedFieldReference(versionField),
              definitionService.getLatestVersionToKey(DefinitionType.PROCESS, definitionKey)));
    } else if (!isDefinitionVersionSetToAll(definitionVersions)) {
      queryBuilder.filter(stringTerms(nestedFieldReference(versionField), definitionVersions));
    } else if (definitionVersions.isEmpty()) {
      // if no version is set just return empty results
      queryBuilder.mustNot(matchAll());
    }
    return queryBuilder;
  }

  private String nestedFieldReference(final String fieldName) {
    return nestedField + "." + fieldName;
  }
}
