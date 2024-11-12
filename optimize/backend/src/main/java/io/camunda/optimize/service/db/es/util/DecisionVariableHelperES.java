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

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public final class DecisionVariableHelperES {

  private DecisionVariableHelperES() {}

  public static BoolQuery.Builder getVariableUndefinedOrNullQuery(
      final String clauseId, final String variablePath, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    final BoolQuery.Builder builder = new BoolQuery.Builder();
    builder
        .should(
            s ->
                s.bool(
                    bol ->
                        bol.mustNot(
                            m ->
                                m.nested(
                                    n ->
                                        n.path(variablePath)
                                            .query(
                                                qu ->
                                                    qu.bool(
                                                        bb ->
                                                            bb.must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getVariableClauseIdField(
                                                                                            variablePath))
                                                                                    .value(
                                                                                        FieldValue
                                                                                            .of(
                                                                                                clauseId))))
                                                                .must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getVariableTypeField(
                                                                                            variablePath))
                                                                                    .value(
                                                                                        FieldValue
                                                                                            .of(
                                                                                                variableTypeId))))))
                                            .scoreMode(ChildScoreMode.None)))))
        .should(
            s ->
                s.bool(
                    bol ->
                        bol.must(
                            m ->
                                m.nested(
                                    n ->
                                        n.path(variablePath)
                                            .query(
                                                qu ->
                                                    qu.bool(
                                                        bb ->
                                                            bb.must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getVariableClauseIdField(
                                                                                            variablePath))
                                                                                    .value(
                                                                                        FieldValue
                                                                                            .of(
                                                                                                clauseId))))
                                                                .must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getVariableTypeField(
                                                                                            variablePath))
                                                                                    .value(
                                                                                        FieldValue
                                                                                            .of(
                                                                                                variableTypeId))))
                                                                .mustNot(
                                                                    mm ->
                                                                        mm.exists(
                                                                            t ->
                                                                                t.field(
                                                                                    getVariableValueField(
                                                                                        variablePath))))))
                                            .scoreMode(ChildScoreMode.None)))))
        .minimumShouldMatch("1");
    return builder;
  }
}
