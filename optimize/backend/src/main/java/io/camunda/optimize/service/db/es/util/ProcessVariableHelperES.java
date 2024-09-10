/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.util;

import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueField;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.index.query.BoolQueryBuilder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessVariableHelperES {

  public static BoolQueryBuilder createFilterForUndefinedOrNullQueryBuilder(
      final String variableName, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return boolQuery()
        .should(
            // undefined
            boolQuery()
                .mustNot(
                    nestedQuery(
                        VARIABLES,
                        boolQuery()
                            .must(termQuery(getNestedVariableNameField(), variableName))
                            .must(termQuery(getNestedVariableTypeField(), variableTypeId)),
                        ScoreMode.None)))
        .should(
            // or null value
            boolQuery()
                .must(
                    nestedQuery(
                        VARIABLES,
                        boolQuery()
                            .must(termQuery(getNestedVariableNameField(), variableName))
                            .must(termQuery(getNestedVariableTypeField(), variableTypeId))
                            .mustNot(existsQuery(getNestedVariableValueField())),
                        ScoreMode.None)))
        .minimumShouldMatch(1);
  }

  public static BoolQueryBuilder createExcludeUndefinedOrNullQueryFilterBuilder(
      final String variableName, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return boolQuery()
        .must(
            nestedQuery(
                VARIABLES,
                boolQuery()
                    .must(termQuery(getNestedVariableNameField(), variableName))
                    .must(termQuery(getNestedVariableTypeField(), variableTypeId))
                    .must(existsQuery(getNestedVariableValueField())),
                ScoreMode.None));
  }
}
