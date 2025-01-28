/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.execution.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractVariableTypingRule implements VariableTypingRule {
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Override
  public final void handle(VariableTypingContext context) {
    if (contextMatches(context)) {
      log.debug(
          "Converting variable {} of process {} from {} to {}",
          context.getVariableName(),
          context.getBpmnProcessId(),
          context.getVariableValue().getClass(),
          targetType(context));
      final Object newVariableValue =
          objectMapper(context).convertValue(context.getVariableValue(), targetType(context));
      context.setVariableValue(newVariableValue);
    }
  }

  protected abstract boolean contextMatches(VariableTypingContext context);

  protected Class<?> targetType(VariableTypingContext context) {
    return targetType();
  }

  protected abstract Class<?> targetType();

  protected ObjectMapper objectMapper(VariableTypingContext context) {
    return objectMapper();
  }

  protected abstract ObjectMapper objectMapper();
}
