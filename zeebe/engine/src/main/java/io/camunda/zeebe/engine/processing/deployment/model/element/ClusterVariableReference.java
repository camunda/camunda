/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.impl.FeelExpression;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.camunda.feel.syntaxtree.ConstContext;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;

/**
 * A reference to a cluster variable in the {@code camunda.vars.<scope>.<name>} format, with an
 * optional trailing field path (e.g. {@code camunda.vars.env.myVar.a.b}), used in an input mapping.
 * {@code scope} is one of {@code env}, {@code tenant} or {@code cluster}.
 *
 * <p>Detection works on the parsed FEEL AST (feel-scala's variable references), not the raw text,
 * so it follows the grammar the engine evaluates. What this implies:
 *
 * <ul>
 *   <li>only expressions are scanned; a static value (no leading {@code =}) is a plain string;
 *   <li>a reference inside a string literal (e.g. {@code ="camunda.vars.env.token"}) stays literal,
 *       so a runtime value that merely looks like a reference is never resolved (injection-safe);
 *   <li>FEEL variants — whitespace, unicode, backtick names, comments — are handled by feel-scala;
 *   <li>unlike a secret reference, a trailing path access into the variable (e.g. {@code
 *       camunda.vars.env.myVar.a.b}) IS part of the reference: feel-scala folds it into one
 *       qualified name, and the segments beyond the variable name are captured as {@code
 *       fieldPath};
 *   <li>the scope whitelist ({@code env}, {@code tenant}, {@code cluster}) excludes {@code
 *       camunda.vars.processInstance.*} and any other non-cluster-variable scope; a bare 3-segment
 *       name (e.g. {@code camunda.vars.env}) is rejected by the four-segment minimum, since it
 *       carries no variable name.
 * </ul>
 *
 * <p>Known gaps (an undetected or less-specific reference is simply not resolved, so nothing
 * leaks): a {@code camunda} bound by an iterator/parameter/context key still reports the reference;
 * and a reference inside a context produced by a non-context expression (e.g. {@code =if c then {x:
 * camunda.vars.env.token} else null}) is reported at the enclosing path, not the inner {@code x}.
 */
@NullMarked
public record ClusterVariableReference(String scope, String name, List<String> fieldPath) {

  private static final String ROOT = "camunda";
  private static final String NAMESPACE = "vars";
  private static final Set<String> SCOPES = Set.of("env", "tenant", "cluster");

  /**
   * Minimum segments a {@code camunda.vars.<scope>.<name>} reference has: root, namespace, scope
   * and name.
   */
  private static final int MIN_SEGMENT_COUNT = 4;

  /** Defensively copies {@code fieldPath} so the record's set membership can't be broken later. */
  public ClusterVariableReference {
    fieldPath = List.copyOf(fieldPath);
  }

  /** Creates a reference to the whole variable, with no trailing field path. */
  public ClusterVariableReference(final String scope, final String name) {
    this(scope, name, List.of());
  }

  /**
   * Parses the cluster-variable references used as expressions in a mapping source, each with the
   * FEEL context path where it occurs ({@code ="prefix" + camunda.vars.env.myVar} → one reference
   * at the empty path). Empty when the source is not a FEEL expression or holds no reference.
   */
  public static List<DetectedClusterVariable> parse(@Nullable final Expression expression) {
    if (!(expression instanceof final FeelExpression feelExpression)) {
      // static values, null sources, and invalid expressions never contain references
      return List.of();
    }
    final var clusterVariables = new ArrayList<DetectedClusterVariable>();
    collect(
        feelExpression.getParsedExpression().expression(), new ArrayDeque<>(), clusterVariables);
    return clusterVariables;
  }

  /**
   * Walks a FEEL AST node, recording each cluster-variable reference with its enclosing context
   * path. A context is descended key by key; any other node is handed to feel-scala for its
   * variable references (which already exclude literals, comments and bound names).
   */
  private static void collect(
      final Exp node,
      final Deque<String> path,
      final List<DetectedClusterVariable> clusterVariables) {
    if (node instanceof final ConstContext context) {
      // keep track of nested paths
      // foo -> {x: camunda.vars.env.x} will have path ["foo", "x"]
      for (final Tuple2<String, Exp> entry : CollectionConverters.asJava(context.entries())) {
        path.addLast(entry._1());
        collect(entry._2(), path, clusterVariables);
        path.removeLast();
      }
      return;
    }
    for (final var reference : new ParsedExpression(node, "").getVariableReferences()) {
      final var qualifiedName = reference.getFullQualifiedName();
      if (isClusterVariableReference(qualifiedName)) {
        final var scope = qualifiedName.get(2);
        final var name = qualifiedName.get(3);
        final var fieldPath = qualifiedName.subList(MIN_SEGMENT_COUNT, qualifiedName.size());
        clusterVariables.add(
            new DetectedClusterVariable(
                List.copyOf(path), new ClusterVariableReference(scope, name, fieldPath)));
      }
    }
  }

  private static boolean isClusterVariableReference(final List<String> qualifiedName) {
    // at least four segments — root, namespace, scope and name; anything beyond that is a field
    // path into the variable. The four-segment minimum (not the scope check) is what rejects a
    // bare 3-segment name such as camunda.vars.env: `env` is a valid scope, so it is the missing
    // name that excludes it. The scope whitelist excludes camunda.vars.processInstance.* and any
    // other non-cluster-variable scope.
    return qualifiedName.size() >= MIN_SEGMENT_COUNT
        && ROOT.equals(qualifiedName.get(0))
        && NAMESPACE.equals(qualifiedName.get(1))
        && SCOPES.contains(qualifiedName.get(2));
  }

  /**
   * A cluster-variable reference with the FEEL context path where it occurs. {@code [x]} for {@code
   * ={x: camunda.vars.env.myVar}}. The path is appended to the mapping target to form the cluster
   * variable leaf's JSON pointer.
   */
  public record DetectedClusterVariable(
      List<String> path, ClusterVariableReference clusterVariable) {}
}
