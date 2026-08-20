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

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.ChildScoreMode;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;

public final class ProcessVariableHelperES {

  private ProcessVariableHelperES() {}

  public static BoolQuery.Builder createFilterForUndefinedOrNullQueryBuilder(
      final String variableName, final VariableType variableType) {
    final String variableTypeId = variableType.getId();

    final BoolQuery.Builder builder = new BoolQuery.Builder();
    builder
        .should(
            s ->
                s.bool(
                    b ->
                        b.mustNot(
                            m ->
                                m.nested(
                                    n ->
                                        n.path(VARIABLES)
                                            .scoreMode(ChildScoreMode.None)
                                            .query(
                                                q ->
                                                    q.bool(
                                                        bb ->
                                                            bb.must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getNestedVariableNameField())
                                                                                    .value(
                                                                                        variableName)))
                                                                .must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getNestedVariableTypeField())
                                                                                    .value(
                                                                                        variableTypeId)))))))))
        .should(
            s ->
                s.bool(
                    b ->
                        b.must(
                            m ->
                                m.nested(
                                    n ->
                                        n.path(VARIABLES)
                                            .scoreMode(ChildScoreMode.None)
                                            .query(
                                                q ->
                                                    q.bool(
                                                        bb ->
                                                            bb.must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getNestedVariableNameField())
                                                                                    .value(
                                                                                        variableName)))
                                                                .must(
                                                                    mm ->
                                                                        mm.term(
                                                                            t ->
                                                                                t.field(
                                                                                        getNestedVariableTypeField())
                                                                                    .value(
                                                                                        variableTypeId)))
                                                                .mustNot(
                                                                    mm ->
                                                                        mm.exists(
                                                                            e ->
                                                                                e.field(
                                                                                    getNestedVariableValueField())))))))))
        .minimumShouldMatch("1");
    return builder;
  }

  public static BoolQuery.Builder createExcludeUndefinedOrNullQueryFilterBuilder(
      final String variableName, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    final BoolQuery.Builder builder = new BoolQuery.Builder();
    builder.must(
        m ->
            m.nested(
                n ->
                    n.path(VARIABLES)
                        .scoreMode(ChildScoreMode.None)
                        .query(
                            q ->
                                q.bool(
                                    bb ->
                                        bb.must(
                                                mm ->
                                                    mm.term(
                                                        t ->
                                                            t.field(getNestedVariableNameField())
                                                                .value(variableName)))
                                            .must(
                                                mm ->
                                                    mm.term(
                                                        t ->
                                                            t.field(getNestedVariableTypeField())
                                                                .value(variableTypeId)))
                                            .must(
                                                mm ->
                                                    mm.exists(
                                                        e ->
                                                            e.field(
                                                                getNestedVariableValueField())))))));
    return builder;
  }
}
