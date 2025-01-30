/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.EvaluationContext;
import org.camunda.feel.context.CustomContext;
import org.camunda.feel.context.VariableProvider;
import org.camunda.feel.syntaxtree.ValContext;
import scala.Option;
import scala.collection.Iterable;

final class FeelVariableContext extends CustomContext {
  private final EvaluationContext context;

  FeelVariableContext(final EvaluationContext context) {
    this.context = context;
  }

  @Override
  public VariableProvider variableProvider() {
    return new EvaluationContextWrapper();
  }

  private final class EvaluationContextWrapper implements VariableProvider {

    @Override
    public Option<Object> getVariable(final String name) {
      final var value = context.getVariable(name);

      if (value != null && value instanceof EvaluationContext) {
        return Option.apply(new ValContext(new FeelVariableContext((EvaluationContext) value)));
      }
      return Option.apply(value);
    }

    @Override
    public Iterable<String> keys() {
      return scala.jdk.javaapi.CollectionConverters.asScala(context.getVariables().toList());
    }

    private ValContext getNamespaceFromContext(final String name) {
      // TODO handle nested scope properly
      return new ValContext(FeelVariableContext.this);
    }
  }
}
