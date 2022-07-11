/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
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
import org.apache.commons.text.StringEscapeUtils;

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
 * <p>Output variable mappings differ from input mappings that the result variables needs to be
 * merged with the existing variables if the variable is a JSON object. The merging is done, as a
 * first draft, by calling the FEEL function 'put all()' and referencing the variable.
 *
 * <pre>
 *   {
 *     a: x,
 *     b: if (b = null)
 *        then {
 *          c: y,
 *          d: z
 *        }
 *        else put all(b, {
 *          c: y,
 *          d: z
 *       })
 *   }
 * </pre>
 *
 * <p>There is one edge case, though, which was revealed in #9543: At the time of this writing the
 * two branches of the if statement behave differently with respect to evaluation errors. The first
 * branch (if the target variable is null) will propagate any error that occurs to the result of the
 * evaluation. However, the second branch uses the put all(...) method which will transform any
 * error in one of its parameters into a null as result. Therefore, the two branches will produce
 * inconsistent results if there are errors in the expression.
 *
 * <p>To account for this the expression, was amended to
 *
 * <pre>
 *  if (targetVar != null and is defined(outputMappingResult))
 *    then put all(targetVar,outputMappingResult)
 *    else outputMappingResult
 * </pre>
 *
 * <p>This is a workaround which hopefully can be removed in the future. It first checks whether the
 * result of the output mapping is defined, and the target variable exists. If this is true, the put
 * all(...) method is called to merge the result into the existing target variable. In all other
 * cases the output mapping result is returned as is. If it evaluates to en error, this error is
 * propagated to the final result of the evaluation
 */
public final class VariableMappingTransformer {

  private static final String EXPRESSION_MARKER = "=";

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
    final var context = asContext(mappings);
    final var contextExpression = asFeelContextExpression(context, this::mergeContextExpression);
    return parseExpression(contextExpression, expressionLanguage);
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
        final String expression;

        if (sourceExpression instanceof StaticExpression) {
          expression =
              String.format(
                  "\"%s\"", StringEscapeUtils.escapeJava(sourceExpression.getExpression()));
        } else {
          expression = sourceExpression.getExpression();
        }

        return targetKey + ":" + expression;
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

  private String mergeContextExpression(
      final String nestedContext, final List<String> contextPath) {
    // for a nested target mapping 'x -> a.b', append the nested property 'b' to
    // the existing context variable 'a' (instead of overriding 'a')
    // example: x = 1 and a = {'c':2} results in a = {'b':1, 'c':2}
    final var existingContext = String.join(".", contextPath);
    return String.format(
        "if (%s != null and is defined(%s)) then put all(%s,%s) else %s",
        existingContext, nestedContext, existingContext, nestedContext, nestedContext);
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
