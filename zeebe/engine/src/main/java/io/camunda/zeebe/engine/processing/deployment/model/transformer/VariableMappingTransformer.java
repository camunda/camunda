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
import io.camunda.zeebe.el.impl.NullExpression;
import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Transform variable mappings into an expression.
 *
 * <p>The resulting expression is a FEEL context that has a similar structure as a JSON document.
 * Each target of a mapping is a key in the context and the source of this mapping is the context
 * value. The source expression can be any FEEL expression. A nested target expression is
 * transformed into a nested context.
 *
 * <p>Variable mappings:
 *
 * <pre>
 *   source | target
 *   =======|=======
 *    x     | a
 *    y     | b.c
 *    z     | b.d
 * </pre>
 *
 * FEEL context expression:
 *
 * <pre>
 *   {
 *     a: x,
 *     b: {
 *       c: y,
 *       d: z
 *     }
 *   }
 * </pre>
 *
 * <p>Output variable mappings build up a {@code _camunda_output_context} context incrementally
 * using {@code context put()}. Each mapping first assigns the source expression to a local variable
 * (so that subsequent mappings can reference it), then adds that variable to the accumulating
 * {@code _camunda_output_context} context.
 *
 * <pre>
 *   {
 *     a: x,
 *     _camunda_output_context: context put({}, "a", a),
 *     b: a + 1,
 *     _camunda_output_context: context put(_camunda_output_context, "b", b)
 *   }._camunda_output_context
 * </pre>
 *
 * <p>For nested target paths (e.g. {@code b.c}), {@code context put()} is called with a path list:
 *
 * <pre>
 *   {
 *     c: y,
 *     _camunda_output_context: context put({}, ["b","c"], c),
 *     d: z,
 *     _camunda_output_context: context put(_camunda_output_context, ["b","d"], d)
 *   }._camunda_output_context
 * </pre>
 */
public final class VariableMappingTransformer {

  private static final String EXPRESSION_MARKER = "=";
  private static final String RESULT_CONTEXT = "_camunda_output_context";

  public Expression transformInputMappings(
      final Collection<? extends ZeebeMapping> inputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(inputMappings, expressionLanguage);
    final var context = asContext(mappings);
    final var contextExpression =
        asFeelContextExpression(context, (contextValue, contextPath) -> contextValue);
    return parseExpression(contextExpression, expressionLanguage);
  }

  public Expression transformOutputMappings(
      final Collection<? extends ZeebeMapping> outputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(outputMappings, expressionLanguage);

    return buildLocalOutputMappingExpression(mappings, expressionLanguage);
  }

  private Expression buildLocalOutputMappingExpression(
      final List<Mapping> mappings, final ExpressionLanguage expressionLanguage) {

    if (mappings.isEmpty()) {
      return parseExpression("{}", expressionLanguage);
    }

    final var sb = new StringBuilder("{");

    for (int i = 0; i < mappings.size(); i++) {
      final var mapping = mappings.get(i);
      final var parts = splitPathExpression(mapping.target);
      final var sourceExpr = formatSourceExpression(mapping.source);
      final var base = (i == 0) ? "{}" : RESULT_CONTEXT;
      final var targetName = parts.getLast();

      if (i > 0) {
        sb.append(",");
      }

      // First, assign the variable so it's available in context for subsequent expressions
      sb.append(String.format("%s:%s,", targetName, sourceExpr));

      // Then, add it to the _camunda_output_context context referencing the just-assigned variable
      if (parts.size() == 1) {
        sb.append(
            String.format(
                "%s:context put(%s,\"%s\",%s)",
                RESULT_CONTEXT, base, parts.getFirst(), targetName));
      } else {
        final var pathList =
            parts.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
        sb.append(
            String.format(
                "%s:context put(%s,[%s],%s)", RESULT_CONTEXT, base, pathList, targetName));
      }
    }

    sb.append(String.format("}.%s", RESULT_CONTEXT));

    return parseExpression(sb.toString(), expressionLanguage);
  }

  private List<Mapping> toMappings(
      final Collection<? extends ZeebeMapping> mappings,
      final ExpressionLanguage expressionLanguage) {
    return mappings.stream()
        .map(
            mapping -> {
              final var source = mapping.getSource();
              final var sourceExpression =
                  source == null
                      ? new NullExpression()
                      : expressionLanguage.parseExpression(source);
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

  private String asFeelContextExpression(
      final MappingContext context,
      final BiFunction<String, List<String>, Object> contextValueVisitor) {
    return context.visit(feelContextBuilder(contextValueVisitor));
  }

  private MappingContextVisitor<String> feelContextBuilder(
      final BiFunction<String, List<String>, Object> contextValueVisitor) {
    return new MappingContextVisitor<>() {
      @Override
      public String onEntry(final String targetKey, final Expression sourceExpression) {
        return targetKey + ":" + formatSourceExpression(sourceExpression);
      }

      @Override
      public String onContext(final List<String> entries) {
        return "{" + String.join(",", entries) + "}";
      }

      @Override
      public String onContextEntry(
          final String targetKey, final String contextValue, final List<String> contextPath) {
        return targetKey + ":" + contextValueVisitor.apply(contextValue, contextPath);
      }
    };
  }

  private static String formatSourceExpression(final Expression sourceExpression) {
    if (sourceExpression instanceof StaticExpression) {
      // due to a regression (https://github.com/camunda/camunda/issues/16043) all the double
      // quotes inside the static expression must be escaped
      return String.format("\"%s\"", sourceExpression.getExpression().replaceAll("\"", "\\\\\""));
    }
    return sourceExpression.getExpression();
  }

  private Expression parseExpression(
      final String contextExpression, final ExpressionLanguage expressionLanguage) {
    final var expression =
        expressionLanguage.parseExpression(EXPRESSION_MARKER + contextExpression);

    if (!expression.isValid()) {
      throw new IllegalStateException(
          String.format(
              "Failed to build variable mapping expression: %s", expression.getFailureMessage()));
    }

    return expression;
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
