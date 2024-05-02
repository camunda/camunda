/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.graphql.error;

import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.kickstart.execution.error.GraphQLErrorHandler;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CustomGraphQLErrorHandler implements GraphQLErrorHandler {
  @Override
  public List<GraphQLError> processErrors(List<GraphQLError> list) {
    return list.stream().map(this::getNested).toList();
  }

  private GraphQLError getNested(GraphQLError error) {
    if (error instanceof ExceptionWhileDataFetching) {
      final ExceptionWhileDataFetching exceptionError = (ExceptionWhileDataFetching) error;
      final Throwable exception = exceptionError.getException();
      if (exception instanceof GraphQLError) {
        return (GraphQLError) exception;
      } else if (exception.getCause() != null
          && exception.getCause() instanceof InvocationTargetException) {
        final InvocationTargetException ite = (InvocationTargetException) exception.getCause();
        if (ite.getTargetException() instanceof GraphQLError) {
          return (GraphQLError) ite.getTargetException();
        } else if (ite.getTargetException() != null) {
          return GraphqlErrorBuilder.newError()
              .message(ite.getTargetException().getMessage())
              .build();
        }
      }
    }
    return error;
  }
}
