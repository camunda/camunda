/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.util;

import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.exists;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableClauseIdField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableTypeField;
import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.NestedQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class DecisionVariableHelperOS {

  private DecisionVariableHelperOS() {}

  public static Query getVariableUndefinedOrNullQuery(
      final String clauseId, final String variablePath, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return new BoolQuery.Builder()
        .should(
            // undefined
            not(
                NestedQuery.of(
                        b ->
                            b.path(variablePath)
                                .query(
                                    and(
                                        term(getVariableClauseIdField(variablePath), clauseId),
                                        term(getVariableTypeField(variablePath), variableTypeId)))
                                .scoreMode(ChildScoreMode.None))
                    .toQuery()))
        .should(
            // or null value
            NestedQuery.of(
                    b ->
                        b.path(variablePath)
                            .query(
                                BoolQuery.of(
                                        b1 ->
                                            b1.must(
                                                    term(
                                                        getVariableClauseIdField(variablePath),
                                                        clauseId))
                                                .must(
                                                    term(
                                                        getVariableTypeField(variablePath),
                                                        variableTypeId))
                                                .mustNot(
                                                    exists(getVariableValueField(variablePath))))
                                    .toQuery())
                            .scoreMode(ChildScoreMode.None))
                .toQuery())
        .minimumShouldMatch("1")
        .build()
        .toQuery();
  }
}
