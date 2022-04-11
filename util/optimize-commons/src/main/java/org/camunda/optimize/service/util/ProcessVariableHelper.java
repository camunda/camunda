/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.MULTIVALUE_FIELD_DATE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.MULTIVALUE_FIELD_DOUBLE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.MULTIVALUE_FIELD_LONG;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessVariableHelper {

  public static String getNestedVariableNameField() {
    return VARIABLES + "." + VARIABLE_NAME;
  }

  public static String getNestedVariableIdField() {
    return VARIABLES + "." + VARIABLE_ID;
  }

  public static String getNestedVariableTypeField() {
    return VARIABLES + "." + VARIABLE_TYPE;
  }

  public static String getNestedVariableValueField() {
    return VARIABLES + "." + VARIABLE_VALUE;
  }

  public static String getValueSearchField(final String searchFieldName) {
    return getNestedVariableValueField() + "." + searchFieldName;
  }

  public static String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }

  public static String getNestedVariableValueFieldForType(final VariableType type) {
    switch (Optional.ofNullable(type).orElseThrow(() -> new IllegalArgumentException("No Type provided"))) {
      case BOOLEAN:
      case STRING:
      case OBJECT:
        return getNestedVariableValueField();
      case DOUBLE:
        return getNestedVariableValueField() + "." + MULTIVALUE_FIELD_DOUBLE;
      case SHORT:
      case INTEGER:
      case LONG:
        return getNestedVariableValueField() + "." + MULTIVALUE_FIELD_LONG;
      case DATE:
        return getNestedVariableValueField() + "." + MULTIVALUE_FIELD_DATE;
      default:
        throw new IllegalArgumentException("Unhandled type: " + type);
    }
  }

  public static BoolQueryBuilder createFilterForUndefinedOrNullQueryBuilder(final String variableName,
                                                                            final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return boolQuery()
      .should(
        // undefined
        boolQuery().mustNot(nestedQuery(
          VARIABLES,
          boolQuery()
            .must(termQuery(getNestedVariableNameField(), variableName))
            .must(termQuery(getNestedVariableTypeField(), variableTypeId)),
          ScoreMode.None
        )))
      .should(
        // or null value
        boolQuery().must(nestedQuery(
          VARIABLES,
          boolQuery()
            .must(termQuery(getNestedVariableNameField(), variableName))
            .must(termQuery(getNestedVariableTypeField(), variableTypeId))
            .mustNot(existsQuery(getNestedVariableValueField())),
          ScoreMode.None
        )))
      .minimumShouldMatch(1);
  }

  public static BoolQueryBuilder createExcludeUndefinedOrNullQueryFilterBuilder(final String variableName,
                                                                                final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return boolQuery()
      .must(nestedQuery(
        VARIABLES,
        boolQuery()
          .must(termQuery(getNestedVariableNameField(), variableName))
          .must(termQuery(getNestedVariableTypeField(), variableTypeId))
          .must(existsQuery(getNestedVariableValueField())),
        ScoreMode.None
      ));
  }

}
