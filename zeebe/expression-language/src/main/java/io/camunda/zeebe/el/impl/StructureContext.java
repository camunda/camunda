/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.ContextValue;
import java.util.Map;
import org.camunda.feel.context.CustomContext;
import org.camunda.feel.context.VariableProvider;
import scala.Option;
import scala.collection.Iterable;
import scala.collection.immutable.VectorMap;
import scala.collection.immutable.VectorMap$;
import scala.jdk.javaapi.CollectionConverters;

/**
 * A FEEL context over a {@link ContextValue.Structure} — an object the caller assembled rather than
 * one read from storage.
 *
 * <p>Unlike {@link FeelVariableContext} this exposes real {@link VariableProvider#keys()}. FEEL
 * derives {@code getVariables()} from {@code keys()}, and {@code FeelToMessagePackTransformer}
 * serialises a context through {@code getVariables()}; without real keys, an expression reading the
 * whole object would serialise it as an empty map.
 *
 * <p>{@code getVariables()} is overridden rather than inherited so the entries keep insertion
 * order. The inherited implementation builds a plain immutable map, which reorders above four
 * entries, and the resulting variable value is compared as an exact JSON string downstream.
 */
final class StructureContext extends CustomContext {

  private final Map<String, ContextValue> entries;

  StructureContext(final Map<String, ContextValue> entries) {
    this.entries = entries;
  }

  @Override
  public VariableProvider variableProvider() {
    return new StructureVariableProvider();
  }

  private final class StructureVariableProvider implements VariableProvider {

    @Override
    public Option<Object> getVariable(final String name) {
      final var entry = entries.get(name);
      return entry == null ? Option.empty() : Option.apply(FeelVariableContext.toFeelValue(entry));
    }

    @Override
    public Iterable<String> keys() {
      return CollectionConverters.asScala(entries.keySet());
    }

    @Override
    public scala.collection.immutable.Map<String, Object> getVariables() {
      VectorMap<String, Object> ordered = VectorMap$.MODULE$.empty();
      for (final var entry : entries.entrySet()) {
        ordered =
            ordered.updated(entry.getKey(), FeelVariableContext.toFeelValue(entry.getValue()));
      }
      return ordered;
    }
  }
}
