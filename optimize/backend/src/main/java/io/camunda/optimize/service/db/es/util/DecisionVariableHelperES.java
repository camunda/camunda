/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.util;

import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableTypeField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;
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
public class DecisionVariableHelperES {
  public static BoolQueryBuilder getVariableUndefinedOrNullQuery(
      final String clauseId, final String variablePath, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return boolQuery()
        .should(
            // undefined
            boolQuery()
                .mustNot(
                    nestedQuery(
                        variablePath,
                        boolQuery()
                            .must(termQuery(getVariableClauseIdField(variablePath), clauseId))
                            .must(termQuery(getVariableTypeField(variablePath), variableTypeId)),
                        ScoreMode.None)))
        .should(
            // or null value
            boolQuery()
                .must(
                    nestedQuery(
                        variablePath,
                        boolQuery()
                            .must(termQuery(getVariableClauseIdField(variablePath), clauseId))
                            .must(termQuery(getVariableTypeField(variablePath), variableTypeId))
                            .mustNot(existsQuery(getVariableValueField(variablePath))),
                        ScoreMode.None)))
        .minimumShouldMatch(1);
  }
}
