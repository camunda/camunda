/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.ContextValue;
import io.camunda.zeebe.el.EvaluationContext;
import org.camunda.feel.context.CustomContext;
import org.camunda.feel.context.VariableProvider;
import org.camunda.feel.syntaxtree.ValContext;
import org.jspecify.annotations.Nullable;
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

  /**
   * Converts a resolved {@link ContextValue} into the object FEEL's value mapper consumes.
   *
   * <p>A {@link ContextValue.MsgPack} stays a buffer, which {@code MessagePackValueMapper} decodes.
   * An {@link ContextValue.Evaluated} that FEEL itself produced hands its {@code Val} straight
   * back: {@code DefaultValueMapper} returns a {@code Val} unchanged, so the type survives. Any
   * other evaluation result — a static or a null expression — has no FEEL value and falls back to
   * its MessagePack form; those are all JSON-representable, so nothing is lost.
   */
  static @Nullable Object toFeelValue(final @Nullable ContextValue value) {
    return switch (value) {
      case null -> null;
      case ContextValue.MsgPack(final var buffer) -> buffer.capacity() > 0 ? buffer : null;
      case ContextValue.Evaluated(final var result) ->
          result instanceof final FeelEvaluationResult feel ? feel.result : result.toBuffer();
      case ContextValue.Structure(final var entries) ->
          new ValContext(new StructureContext(entries));
    };
  }

  private final class EvaluationContextWrapper implements VariableProvider {

    @Override
    public Option<Object> getVariable(final String name) {
      return context
          .getVariable(name)
          .fold(
              value -> Option.apply(toFeelValue(value)),
              evaluationContext ->
                  Option.apply(new ValContext(new FeelVariableContext(evaluationContext))));
    }

    @Override
    public Iterable<String> keys() {
      return List$.MODULE$.empty();
    }
  }
}
