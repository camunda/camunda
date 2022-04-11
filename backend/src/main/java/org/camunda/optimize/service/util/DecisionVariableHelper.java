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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.MULTIVALUE_FIELD_DATE;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.MULTIVALUE_FIELD_DOUBLE;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.MULTIVALUE_FIELD_LONG;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_CLAUSE_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_VALUE;
import static org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex.VARIABLE_VALUE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DecisionVariableHelper {
  private static final List<VariableType> MULTIVALUE_TYPE_FIELDS = Collections.unmodifiableList(Arrays.asList(
    VariableType.DATE, VariableType.DOUBLE, VariableType.LONG
  ));

  public static String getVariableValueField(final String variablePath) {
    return variablePath + "." + VARIABLE_VALUE;
  }

  public static List<VariableType> getVariableMultivalueFields() {
    return MULTIVALUE_TYPE_FIELDS;
  }

  public static String getVariableStringValueField(final String variablePath) {
    return getVariableValueFieldForType(variablePath, VariableType.STRING);
  }

  public static String getValueSearchField(final String variablePath, final String searchFieldName) {
    return getVariableStringValueField(variablePath) + "." + searchFieldName;
  }

  public static String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }

  public static String getVariableValueFieldForType(final String variablePath, final VariableType type) {
    switch (Optional.ofNullable(type).orElseThrow(() -> new IllegalArgumentException("No Type provided"))) {
      case BOOLEAN:
      case STRING:
        return getVariableValueField(variablePath);
      case DOUBLE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_DOUBLE;
      case SHORT:
      case INTEGER:
      case LONG:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_LONG;
      case DATE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_DATE;
      default:
        throw new IllegalArgumentException("Unhandled type: " + type);
    }
  }

  public static String getVariableClauseIdField(final String variablePath) {
    return variablePath + "." + VARIABLE_CLAUSE_ID;
  }

  public static String getVariableTypeField(final String variablePath) {
    return variablePath + "." + VARIABLE_VALUE_TYPE;
  }

  public static BoolQueryBuilder getVariableUndefinedOrNullQuery(final String clauseId,
                                                                 final String variablePath,
                                                                 final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return boolQuery()
      .should(
        // undefined
        boolQuery().mustNot(nestedQuery(
          variablePath,
          boolQuery()
            .must(termQuery(getVariableClauseIdField(variablePath), clauseId))
            .must(termQuery(getVariableTypeField(variablePath), variableTypeId)),
          ScoreMode.None
        )))
      .should(
        // or null value
        boolQuery().must(nestedQuery(
          variablePath,
          boolQuery()
            .must(termQuery(getVariableClauseIdField(variablePath), clauseId))
            .must(termQuery(getVariableTypeField(variablePath), variableTypeId))
            .mustNot(existsQuery(getVariableValueField(variablePath))),
          ScoreMode.None
        )))
      .minimumShouldMatch(1);
  }

}
