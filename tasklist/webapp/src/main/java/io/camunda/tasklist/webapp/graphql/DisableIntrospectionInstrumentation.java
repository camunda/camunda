/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;

public class DisableIntrospectionInstrumentation extends SimplePerformantInstrumentation {

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(
      final InstrumentationExecuteOperationParameters parameters,
      final InstrumentationState state) {
    final ExecutionContext executionContext = parameters.getExecutionContext();
    final OperationDefinition operationDefinition = executionContext.getOperationDefinition();
    if (operationDefinition != null && operationDefinition.getOperation() == Operation.QUERY) {
      if (executionContext.getExecutionInput() != null
          && isIntrospectionQuery(executionContext.getExecutionInput().getQuery())) {
        throw new UnsupportedOperationException("GraphQL introspection is disabled");
      }
    }
    return super.beginExecuteOperation(parameters, state);
  }

  private boolean isIntrospectionQuery(final String query) {
    return query != null && query.contains("__schema") || query.contains("__type");
  }
}
