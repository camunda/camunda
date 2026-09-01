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
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference;
import io.camunda.zeebe.engine.processing.deployment.model.element.ClusterVariableReference.DetectedClusterVariable;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference.DetectedSecret;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
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
 * <p>Input and output mappings differ in how a nested target relates to the existing scope variable
 * at runtime, and each difference is handled by its own {@code MappingResultBuilder}
 * implementation. An output mapping's result is merged into that variable if it is a JSON object,
 * at every nesting level, so the merged value is what gets written ({@code
 * OutputMappingResultBuilder}). An input mapping's result is not merged — it is written as-is — but
 * when a later mapping in the same list reads that root name in its own source, it sees the mapped
 * keys layered over the value the scope chain resolves, so a mapping shadows only the keys it
 * defines ({@code InputMappingResultBuilder}).
 */
public final class VariableMappingTransformer {

  private static final String EXPRESSION_MARKER = "=";

  /**
   * Transforms the input mappings, keeping each mapping as its own source expression plus target
   * path so they can be evaluated one by one in modeling order at runtime and, in the same pass,
   * detects the secret references they use (see {@link #detectSecretReferences}) and precomputes
   * the combined FEEL context expression the legacy resolver evaluates (see {@link
   * #asFeelContextExpression}).
   */
  public InputMappings transformInputMappings(
      final Collection<? extends ZeebeMapping> inputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(inputMappings, expressionLanguage);
    final var context = asContext(mappings);
    final var contextExpression =
        asFeelContextExpression(context, (contextValue, contextPath) -> contextValue);
    final var combinedExpression = parseExpression(contextExpression, expressionLanguage);

    final var transformedMappings =
        mappings.stream()
            .map(
                mapping ->
                    new InputMapping(
                        toInputSourceExpression(mapping.source, expressionLanguage),
                        splitPathExpression(mapping.target)))
            .toList();
    return new InputMappings(
        transformedMappings,
        combinedExpression,
        detectSecretReferences(context),
        detectClusterVariableReferences(context));
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
          // due to a regression (https://github.com/camunda/camunda/issues/16043) all the double
          // quotes inside the static expression must be escaped
          expression =
              String.format("\"%s\"", sourceExpression.getExpression().replaceAll("\"", "\\\\\""));
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
        "if (%s != null) then context merge(%s,%s) else %s",
        existingContext, existingContext, nestedContext, nestedContext);
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
   * path so they can be evaluated one by one in modeling order at runtime, and also precomputes the
   * combined FEEL context expression for {@code CombinedInputMappingResolver} to evaluate on each
   * completion without rebuilding it.
   *
   * <p>A nested target merges with the existing scope value at every path level in ORDERED mode
   * (see {@code OutputMappingResultBuilder}). In COMBINED mode the pre-built expression evaluates
   * all mappings in a single FEEL context literal against the outer (job-variable) scope.
   */
  public OutputMappings transformOutputMappings(
      final Collection<? extends ZeebeMapping> outputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(outputMappings, expressionLanguage);
    final var context = asContext(mappings);
    final var contextExpression = asFeelContextExpression(context, this::mergeContextExpression);
    final var combinedExpression = parseExpression(contextExpression, expressionLanguage);

    final var transformedMappings =
        mappings.stream()
            .map(mapping -> new OutputMapping(mapping.source, splitPathExpression(mapping.target)))
            .toList();
    return new OutputMappings(combinedExpression, transformedMappings);
  }

  /**
   * Detects the secrets referenced by the built mapping context, keyed by the JSON pointer (RFC
   * 6901) of the leaf each secret belongs to — the leaf's target path plus the reference's FEEL
   * context path. Examples: {@code "Bearer " + camunda.secrets.token -> tokens.t} gives {@code
   * /tokens/t}; {@code {x2: camunda.secrets.x} -> foo} gives {@code /foo/x2}. The pointer lets job
   * activation replace the reference in the job variables via a Jackson {@code JsonPointer}; the
   * conversion happens once, here at deploy time.
   *
   * <p>Walking the built context (not the raw mappings) means overridden targets are already
   * resolved: a target overwritten by a later mapping contributes no secret. Mappings with no
   * reference are omitted (see {@link SecretReference} for what counts).
   *
   * <p>The pointer is leaf-precise only for references in literal FEEL contexts. A reference inside
   * a context produced by another expression (e.g. an {@code if} that returns a context) is keyed
   * at the enclosing target, not the exact leaf — see {@link SecretReference} for this limitation.
   */
  private static Map<String, Set<SecretReference>> detectSecretReferences(
      final MappingContext context) {
    final var secretsByPointer = new LinkedHashMap<String, Set<SecretReference>>();
    for (final var detected : context.visit(secretCollector())) {
      secretsByPointer
          .computeIfAbsent(toJsonPointer(detected.path()), key -> new LinkedHashSet<>())
          .add(detected.secret());
    }
    return secretsByPointer;
  }

  /**
   * Visitor that collects the secrets of a mapping context as a flat list, each carrying the path
   * segments of its leaf: the target path (built up from the context keys as the visit descends)
   * plus the reference's FEEL context path within the source. A nested context is descended; a leaf
   * holds the source expression to scan.
   */
  private static MappingContextVisitor<List<DetectedSecret>> secretCollector() {
    return new MappingContextVisitor<>() {
      @Override
      public List<DetectedSecret> onEntry(final String targetKey, final Expression source) {
        return SecretReference.parse(source).stream()
            .map(detected -> prependKey(targetKey, detected))
            .collect(Collectors.toList());
      }

      @Override
      public List<DetectedSecret> onContext(final List<List<DetectedSecret>> entries) {
        return entries.stream().flatMap(List::stream).collect(Collectors.toList());
      }

      @Override
      public List<DetectedSecret> onContextEntry(
          final String targetKey,
          final List<DetectedSecret> contextValue,
          final List<String> contextPath) {
        return contextValue.stream()
            .map(detected -> prependKey(targetKey, detected))
            .collect(Collectors.toList());
      }
    };
  }

  private static DetectedSecret prependKey(final String key, final DetectedSecret detected) {
    final var path = new ArrayList<String>();
    path.add(key);
    path.addAll(detected.path());
    return new DetectedSecret(path, detected.secret());
  }

  /**
   * Detects the cluster variables referenced by the built mapping context, keyed by the JSON
   * pointer (RFC 6901) of the leaf each reference belongs to — the leaf's target path plus the
   * reference's FEEL context path. Mirrors {@link #detectSecretReferences} for cluster-variable
   * references (see {@link ClusterVariableReference} for what counts).
   */
  private static Map<String, Set<ClusterVariableReference>> detectClusterVariableReferences(
      final MappingContext context) {
    final var clusterVariablesByPointer =
        new LinkedHashMap<String, Set<ClusterVariableReference>>();
    for (final var detected : context.visit(clusterVariableCollector())) {
      clusterVariablesByPointer
          .computeIfAbsent(toJsonPointer(detected.path()), key -> new LinkedHashSet<>())
          .add(detected.clusterVariable());
    }
    return clusterVariablesByPointer;
  }

  /**
   * Visitor that collects the cluster-variable references of a mapping context as a flat list, each
   * carrying the path segments of its leaf: the target path (built up from the context keys as the
   * visit descends) plus the reference's FEEL context path within the source. A nested context is
   * descended; a leaf holds the source expression to scan.
   */
  private static MappingContextVisitor<List<DetectedClusterVariable>> clusterVariableCollector() {
    return new MappingContextVisitor<>() {
      @Override
      public List<DetectedClusterVariable> onEntry(
          final String targetKey, final Expression source) {
        return ClusterVariableReference.parse(source).stream()
            .map(detected -> prependKey(targetKey, detected))
            .collect(Collectors.toList());
      }

      @Override
      public List<DetectedClusterVariable> onContext(
          final List<List<DetectedClusterVariable>> entries) {
        return entries.stream().flatMap(List::stream).collect(Collectors.toList());
      }

      @Override
      public List<DetectedClusterVariable> onContextEntry(
          final String targetKey,
          final List<DetectedClusterVariable> contextValue,
          final List<String> contextPath) {
        return contextValue.stream()
            .map(detected -> prependKey(targetKey, detected))
            .collect(Collectors.toList());
      }
    };
  }

  private static DetectedClusterVariable prependKey(
      final String key, final DetectedClusterVariable detected) {
    final var path = new ArrayList<String>();
    path.add(key);
    path.addAll(detected.path());
    return new DetectedClusterVariable(path, detected.clusterVariable());
  }

  /**
   * Builds the RFC 6901 JSON pointer of a secret's leaf from its path segments (target path plus
   * the reference's FEEL context path), so job activation can address it directly in the
   * job-variables document. Examples: {@code [tokens, token]} → {@code /tokens/token}; {@code [foo,
   * x2]} → {@code /foo/x2}.
   *
   * <p>{@code ~} and {@code /} are escaped ({@code ~} → {@code ~0}, {@code /} → {@code ~1}) because
   * both are reserved in a pointer: an unescaped {@code /} inside a segment (e.g. a backtick FEEL
   * name like {@code `a/b`}) would be read as a separator and wrongly split it. {@code ~} is
   * replaced first, otherwise the {@code ~1} produced for {@code /} would be escaped again.
   */
  private static String toJsonPointer(final List<String> segments) {
    final var pointer = new StringBuilder();
    for (final String segment : segments) {
      pointer.append('/').append(segment.replace("~", "~0").replace("/", "~1"));
    }
    return pointer.toString();
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
