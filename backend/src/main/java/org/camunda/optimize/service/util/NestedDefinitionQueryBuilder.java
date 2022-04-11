/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.service.DefinitionService;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.List;

import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToAll;
import static org.camunda.optimize.service.util.DefinitionVersionHandlingUtil.isDefinitionVersionSetToLatest;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@AllArgsConstructor
public class NestedDefinitionQueryBuilder {

  private final String nestedField;
  private final String definitionKeyField;
  private final String versionField;
  private final String tenantIdField;

  public QueryBuilder createNestedDocDefinitionQuery(final String definitionKey,
                                                     final List<String> definitionVersions,
                                                     final List<String> tenantIds,
                                                     final DefinitionService definitionService) {
    final BoolQueryBuilder query = boolQuery();
    query.filter(DefinitionQueryUtil.createTenantIdQuery(nestedFieldReference(tenantIdField), tenantIds));
    query.filter(termQuery(nestedFieldReference(definitionKeyField), definitionKey));
    if (isDefinitionVersionSetToLatest(definitionVersions)) {
      query.filter(termsQuery(
        nestedFieldReference(versionField),
        definitionService.getLatestVersionToKey(DefinitionType.PROCESS, definitionKey)
      ));
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
