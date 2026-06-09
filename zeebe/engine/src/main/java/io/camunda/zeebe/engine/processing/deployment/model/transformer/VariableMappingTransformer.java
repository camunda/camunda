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
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transform variable mappings into an expression.
 *
 * <p>The resulting expression is a FEEL context that preserves the order of mappings and allows
 * subsequent mappings to reference variables from previous mappings.
 *
 * <p>Both input and output variable mappings build up a result context incrementally using {@code
 * context put()}. Each mapping first assigns the source expression to a local variable (so that
 * subsequent mappings can reference it), then adds that variable to the accumulating result
 * context.
 *
 * <p>Input variable mappings example:
 *
 * <pre>
 *   Variable mappings:
 *   source      | target
 *   ============|============
 *   x           | a
 *   a + 1       | b
 *
 *   Generated expression:
 *   {
 *     a: x,
 *     _camunda_input_context: context put({}, "a", a),
 *     b: a + 1,
 *     _camunda_input_context: context put(_camunda_input_context, "b", b)
 *   }._camunda_input_context
 * </pre>
 *
 * <p>Output variable mappings example:
 *
 * <pre>
 *   Variable mappings:
 *   source      | target
 *   ============|============
 *   x           | a
 *   a + 1       | b
 *
 *   Generated expression:
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
 *     _camunda_input_context: context put({}, ["b","c"], c),
 *     d: z,
 *     _camunda_input_context: context put(_camunda_input_context, ["b","d"], d)
 *   }._camunda_input_context
 * </pre>
 */
public final class VariableMappingTransformer {

  private static final String EXPRESSION_MARKER = "=";
  private static final String INPUT_RESULT_CONTEXT = "_camunda_input_context";
  private static final String OUTPUT_RESULT_CONTEXT = "_camunda_output_context";

  public Expression transformInputMappings(
      final Collection<? extends ZeebeMapping> inputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(inputMappings, expressionLanguage);

    return buildLocalInputMappingExpression(mappings, expressionLanguage);
  }

  public Expression transformOutputMappings(
      final Collection<? extends ZeebeMapping> outputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(outputMappings, expressionLanguage);

    return buildLocalOutputMappingExpression(mappings, expressionLanguage);
  }

  private Expression buildLocalInputMappingExpression(
      final List<Mapping> mappings, final ExpressionLanguage expressionLanguage) {
    return buildIncrementalMappingExpressionWithNestedVariableSupport(
        mappings, expressionLanguage, INPUT_RESULT_CONTEXT);
  }

  private Expression buildLocalOutputMappingExpression(
      final List<Mapping> mappings, final ExpressionLanguage expressionLanguage) {
    return buildIncrementalMappingExpression(mappings, expressionLanguage, OUTPUT_RESULT_CONTEXT);
  }

  /**
   * Builds an incremental mapping expression using {@code context put()}.
   *
   * <p>Each mapping first assigns the source expression to a local variable (so that subsequent
   * mappings can reference it), then adds that variable to the accumulating result context.
   *
   * <p>This approach ensures:
   *
   * <ul>
   *   <li>Mappings are evaluated in definition order
   *   <li>Subsequent mappings can reference variables from previous mappings
   *   <li>Nested target paths are handled correctly without premature evaluation
   * </ul>
   *
   * @param mappings the list of mappings to process
   * @param expressionLanguage the expression language to parse the result
   * @param resultContextName the name of the accumulator context variable
   * @return the parsed expression
   */
  private Expression buildIncrementalMappingExpression(
      final List<Mapping> mappings,
      final ExpressionLanguage expressionLanguage,
      final String resultContextName) {

    if (mappings.isEmpty()) {
      return parseExpression("{}", expressionLanguage);
    }

    final var sb = new StringBuilder("{");

    for (int i = 0; i < mappings.size(); i++) {
      final var mapping = mappings.get(i);
      final var parts = splitPathExpression(mapping.target());
      final var sourceExpr = formatSourceExpression(mapping.source());
      final var base = (i == 0) ? "{}" : resultContextName;
      final var targetName = parts.getLast();

      if (i > 0) {
        sb.append(",");
      }

      // First, assign the variable so it's available in context for subsequent expressions
      sb.append(String.format("%s:%s,", targetName, sourceExpr));

      // Then, add it to the result context referencing the just-assigned variable
      if (parts.size() == 1) {
        sb.append(
            String.format(
                "%s:context put(%s,\"%s\",%s)",
                resultContextName, base, parts.getFirst(), targetName));
      } else {
        final var pathList =
            parts.stream().map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
        sb.append(
            String.format(
                "%s:context put(%s,[%s],%s)", resultContextName, base, pathList, targetName));
      }
    }

    sb.append(String.format("}.%s", resultContextName));

    return parseExpression(sb.toString(), expressionLanguage);
  }

  /**
   * Builds an incremental mapping expression using {@code context put()}.
   *
   * <p>Each mapping first assigns the source expression to a local variable (so that subsequent
   * mappings can reference it), then adds that variable to the accumulating result context.
   *
   * <p>This approach ensures:
   *
   * <ul>
   *   <li>Mappings are evaluated in definition order
   *   <li>Subsequent mappings can reference variables from previous mappings
   *   <li>Nested target paths are handled correctly without premature evaluation
   * </ul>
   *
   * Compared to {@link #buildIncrementalMappingExpression}, this method adds support for nested
   * variable references in source expressions of subsequent mappings.
   *
   * @param mappings the list of mappings to process
   * @param expressionLanguage the expression language to parse the result
   * @param resultContextName the name of the accumulator context variable
   * @return the parsed expression
   */
  private Expression buildIncrementalMappingExpressionWithNestedVariableSupport(
      final List<Mapping> mappings,
      final ExpressionLanguage expressionLanguage,
      final String resultContextName) {

    if (mappings.isEmpty()) {
      return parseExpression("{}", expressionLanguage);
    }

    final var sb = new StringBuilder("{");

    for (int i = 0; i < mappings.size(); i++) {
      final var mapping = mappings.get(i);
      final var parts = splitPathExpression(mapping.target());
      final var sourceExpr = formatSourceExpression(mapping.source());
      final var base = (i == 0) ? "{}" : resultContextName;

      if (i > 0) {
        sb.append(",");
      }

      // First, assign the variable so it's available in context for subsequent expressions
      if (parts.size() == 1) {
        sb.append(String.format("%s:%s,", mapping.target(), sourceExpr));
      } else {
        // If it is a nested variable, it is necessary to ensure that the root variable is available
        // and then it should be updated with `context put` (assigning to the nested variable
        // directly will create a new flat variable with "." in its name)
        final var root = parts.getFirst();
        // Note: the following expression is necessary to handle three cases:
        // - root variable does not exist yet
        //   - `get entries` will return null (as the variable is not found)
        //   - `get or else` will return an empty list
        //   - `context` will convert it to an empty context object
        // - root variable exists but contains a scalar
        //   - `get entries` will return null (as the variable is not a context)
        //   - `get or else` will return an empty list
        //   - `context` will convert it to an empty context object
        // - root variable exists and it is an object
        //   - `get entries` will return the contained entries (as it is in fact a context)
        //   - `get or else` will return the contained entries
        //   - `context` will rebuild the context object exactly as it was
        sb.append(String.format("%s:context(get or else(get entries(%s),[])),", root, root));
        final var subPath = parts.stream().skip(1).toList();
        sb.append(buildContextUpdate(root, root, subPath, sourceExpr)).append(",");
      }

      // Then, add it to the result context referencing the just-assigned variable
      sb.append(buildContextUpdate(resultContextName, base, parts, mapping.target()));
    }

    sb.append(String.format("}.%s", resultContextName));

    return parseExpression(sb.toString(), expressionLanguage);
  }

  private String buildContextUpdate(
      final String targetContext,
      final String sourceContext,
      final List<String> path,
      final String value) {
    final String pathList;
    if (path.size() == 1) {
      pathList = String.format("\"%s\"", path.getFirst());
    } else {
      pathList = path.stream().collect(Collectors.joining("\",\"", "[\"", "\"]"));
    }
    return String.format("%s:context put(%s,%s,%s)", targetContext, sourceContext, pathList, value);
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

  private List<String> splitPathExpression(final String path) {
    final var parts = path.split("\\.");
    return new ArrayList<>(Arrays.asList(parts));
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

  private record Mapping(Expression source, String target) {}
}
