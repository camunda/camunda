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
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.nested;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.not;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableNameField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableTypeField;
import static io.camunda.optimize.service.db.util.ProcessVariableHelper.getNestedVariableValueField;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public final class ProcessVariableHelperOS {

  private ProcessVariableHelperOS() {}

  public static Query createFilterForUndefinedOrNullQuery(
      final String variableName, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return new BoolQuery.Builder()
        .should(
            // undefined
            not(
                nested(
                    VARIABLES,
                    and(
                        term(getNestedVariableNameField(), variableName),
                        term(getNestedVariableTypeField(), variableTypeId)),
                    ChildScoreMode.None)))
        .should(
            // or null value
            nested(
                VARIABLES,
                and(
                    term(getNestedVariableNameField(), variableName),
                    term(getNestedVariableTypeField(), variableTypeId),
                    not(exists(getNestedVariableValueField()))),
                ChildScoreMode.None))
        .minimumShouldMatch("1")
        .build()
        .toQuery();
  }

  public static Query createExcludeUndefinedOrNullQueryFilter(
      final String variableName, final VariableType variableType) {
    final String variableTypeId = variableType.getId();
    return nested(
        VARIABLES,
        and(
            term(getNestedVariableNameField(), variableName),
            term(getNestedVariableTypeField(), variableTypeId),
            exists(getNestedVariableValueField())),
        ChildScoreMode.None);
  }
}
