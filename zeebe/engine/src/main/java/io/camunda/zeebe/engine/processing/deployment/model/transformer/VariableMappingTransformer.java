/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transforms variable mappings.
 *
 * <p>Neither input nor output mappings are combined into a single context expression. Each mapping
 * is kept as its own parsed source expression plus its split target path (e.g. target {@code a.b.c}
 * becomes {@code [a, b, c]}), so that mappings can be evaluated one by one in modeling order at
 * runtime, with each mapping's result visible to subsequent mappings (see {@link InputMapping},
 * {@link OutputMapping} and {@code BpmnVariableMappingBehavior}).
 *
 * <p>Output mappings differ from input mappings in how a nested target is merged at runtime: the
 * result must be merged with the existing scope variable if that variable is a JSON object, at
 * every nesting level. This merging is done by {@code MappingResultBuilder} while accumulating
 * results.
 */
public final class VariableMappingTransformer {

  private static final String EXPRESSION_MARKER = "=";

  /**
   * Transforms the input mappings, keeping each mapping as its own source expression plus target
   * path so they can be evaluated one by one in modeling order at runtime.
   */
  public InputMappings transformInputMappings(
      final Collection<? extends ZeebeMapping> inputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(inputMappings, expressionLanguage);

    final var transformedMappings =
        mappings.stream()
            .map(
                mapping ->
                    new InputMapping(
                        toInputSourceExpression(mapping.source, expressionLanguage),
                        splitPathExpression(mapping.target)))
            .toList();
    return new InputMappings(transformedMappings);
  }

  private static Expression toInputSourceExpression(
      final Expression source, final ExpressionLanguage expressionLanguage) {
    if (source instanceof StaticExpression) {
      // A static input source is treated as a string literal, byte-identical to the previous
      // combined FEEL context expression (including the #16043 double-quote escaping): e.g. source
      // "1" stays the string "1" instead of being type-inferred to the number 1. FEEL and null
      // sources are left untouched.
      final var escaped = source.getExpression().replaceAll("\"", "\\\\\"");
      return expressionLanguage.parseExpression(EXPRESSION_MARKER + "\"" + escaped + "\"");
    }
    return source;
  }

  /**
   * Transforms the output mappings, keeping each mapping as its own source expression plus target
   * path so they can be evaluated one by one in modeling order at runtime. A nested target merges
   * with the existing scope value at every path level at runtime (see {@code
   * MappingResultBuilder}).
   */
  public List<OutputMapping> transformOutputMappings(
      final Collection<? extends ZeebeMapping> outputMappings,
      final ExpressionLanguage expressionLanguage) {

    return toMappings(outputMappings, expressionLanguage).stream()
        .map(mapping -> new OutputMapping(mapping.source, splitPathExpression(mapping.target)))
        .toList();
  }

  private List<Mapping> toMappings(
      final Collection<? extends ZeebeMapping> mappings,
      final ExpressionLanguage expressionLanguage) {
    return mappings.stream()
        .map(
            mapping -> {
              final var source = mapping.getSource();
              final var sourceExpression = expressionLanguage.parseExpression(source);
              return new Mapping(sourceExpression, mapping.getTarget());
            })
        .collect(Collectors.toList());
  }

  private MappingContext asContext(final List<Mapping> mappings) {
    final var context = new MappingContext();

    for (final Mapping mapping : mappings) {
      final var sourceExpression = mapping.source;
      final var targetPathExpression = mapping.target;

      final var targetPathParts = splitPathExpression(targetPathExpression);
      createContextEntry(targetPathParts, sourceExpression, context);
    }
    return context;
  }

  private List<String> splitPathExpression(final String path) {
    final var parts = path.split("\\.");
    return new ArrayList<>(Arrays.asList(parts));
  }

  private void createContextEntry(
      final List<String> targetPathParts,
      final Expression sourceExpression,
      final MappingContext context) {
    final String target = targetPathParts.remove(0);

    if (targetPathParts.isEmpty()) {
      context.addEntry(target, sourceExpression);

    } else {
      final var nestedContext = context.getOrAddContext(target);
      createContextEntry(targetPathParts, sourceExpression, nestedContext);
    }
  }

  private static final class MappingContext {

    private final Map<String, Object> entries = new LinkedHashMap<>();

    private final List<String> path;

    public MappingContext() {
      path = new ArrayList<>();
    }

    public MappingContext(final List<String> path) {
      this.path = path;
    }

    public void addEntry(final String key, final Expression value) {
      entries.put(key, value);
    }

    public MappingContext getOrAddContext(final String key) {
      final var entry = entries.get(key);

      if (entry instanceof MappingContext) {
        return (MappingContext) entry;

      } else {
        final var nestedPath = new ArrayList<>(path);
        nestedPath.add(key);
        final var nestedContext = new MappingContext(nestedPath);
        entries.put(key, nestedContext);

        return nestedContext;
      }
    }

    public <T> T visit(final MappingContextVisitor<T> visitor) {
      final var entries =
          this.entries.entrySet().stream()
              .map(
                  entry -> {
                    final var key = entry.getKey();
                    final var value = entry.getValue();

                    if (value instanceof final MappingContext nestedContext) {
                      final var contextValue = nestedContext.visit(visitor);

                      return visitor.onContextEntry(key, contextValue, nestedContext.path);
                    } else {
                      return visitor.onEntry(key, (Expression) value);
                    }
                  })
              .collect(Collectors.toList());

      return visitor.onContext(entries);
    }
  }

  private static final class Mapping {

    private final Expression source;
    private final String target;

    private Mapping(final Expression source, final String target) {
      this.source = source;
      this.target = target;
    }
  }

  private interface MappingContextVisitor<T> {
    T onEntry(String source, Expression target);

    T onContext(List<T> entries);

    T onContextEntry(final String target, final T contextValue, final List<String> contextPath);
  }
}
