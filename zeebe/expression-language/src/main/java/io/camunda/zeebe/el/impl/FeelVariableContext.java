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
import scala.collection.immutable.List$;

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
      return context
          .getVariable(name)
          .fold(
              directBuffer ->
                  Option.when(
                      directBuffer != null && directBuffer.capacity() > 0, () -> directBuffer),
              evaluationContext ->
                  Option.apply(new ValContext(new FeelVariableContext(evaluationContext))));
    }

    @Override
    public Iterable<String> keys() {
      return List$.MODULE$.empty();
    }
  }
}
