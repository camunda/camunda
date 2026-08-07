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
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Transforms variable mappings.
 *
 * <p>Neither input nor output mappings are combined into a single context expression for normal
 * evaluation. Each mapping is kept as its own parsed source expression plus its split target path
 * (e.g. target {@code a.b.c} becomes {@code [a, b, c]}), so that mappings can be evaluated one by
 * one in modeling order at runtime, with each mapping's result visible to subsequent mappings (see
 * {@link InputMapping}, {@link OutputMapping} and {@code BpmnVariableMappingBehavior}).
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
   * path so they can be evaluated one by one in modeling order at runtime and, in the same pass,
   * detects the secret references they use (see {@link #detectSecretReferences}).
   */
  public InputMappings transformInputMappings(
      final Collection<? extends ZeebeMapping> inputMappings,
      final ExpressionLanguage expressionLanguage) {

    final var mappings = toMappings(inputMappings, expressionLanguage);
    final var context = asContext(mappings);

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
        detectSecretReferences(context),
        detectClusterVariableReferences(context),
        combinedExpression(context, expressionLanguage));
  }

  /**
   * Builds the single FEEL context expression the input mappings compiled into before they were
   * evaluated one by one — {@code x -> a, y -> b.c} becomes {@code ={a:x,b:{c:y}}} — so the {@code
   * evaluateInputMappingsOneByOne} kill-switch can restore that evaluation without a redeployment.
   * Built for every process, because the flag is broker configuration the transformer must not
   * read: it may flip after the process was transformed into the cache.
   *
   * <p>Returns {@code null} when the combined expression does not parse, instead of rejecting the
   * deployment the way the pre-{@code #58801} transformer did. No target that passes deploy-time
   * validation is currently known to produce one: the FEEL keywords that {@code
   * ZeebeExpressionValidator}'s reserved list does not cover ({@code some}, {@code every}, {@code
   * return}, ...) are all accepted as context keys, which a test pins. So this is defense in depth,
   * not a reproduced hazard — but the two alternatives are both worse than a null. Throwing would
   * reject such a process at deployment for everyone, including the vast majority who never touch
   * the kill-switch; failing at runtime would make it permanently un-activatable the moment someone
   * flips the flag, with no incident to resolve.
   */
  private static @Nullable Expression combinedExpression(
      final MappingContext context, final ExpressionLanguage expressionLanguage) {
    final var expression =
        expressionLanguage.parseExpression(EXPRESSION_MARKER + context.visit(feelContextBuilder()));
    return expression.isValid() ? expression : null;
  }

  /**
   * Visitor that renders a mapping context as the FEEL context expression text: a leaf becomes
   * {@code target:source}, a nested context {@code target:{...}}. A static source is quoted as a
   * string literal, with the {@code #16043} double-quote escaping.
   */
  private static MappingContextVisitor<String> feelContextBuilder() {
    return new MappingContextVisitor<>() {
      @Override
      public String onEntry(final String targetKey, final Expression sourceExpression) {
        final var expression =
            sourceExpression instanceof StaticExpression
                ? "\"" + sourceExpression.getExpression().replaceAll("\"", "\\\\\"") + "\""
                : sourceExpression.getExpression();
        return targetKey + ":" + expression;
      }

      @Override
      public String onContext(final List<String> entries) {
        return "{" + String.join(",", entries) + "}";
      }

      @Override
      public String onContextEntry(
          final String targetKey, final String contextValue, final List<String> contextPath) {
        return targetKey + ":" + contextValue;
      }
    };
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
