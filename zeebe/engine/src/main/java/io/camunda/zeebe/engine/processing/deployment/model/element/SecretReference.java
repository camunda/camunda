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
import org.camunda.feel.syntaxtree.ConstContext;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;

/**
 * A reference to a secret in the {@code camunda.secrets.<name>} format, used in an input mapping.
 *
 * <p>Detection works on the parsed FEEL AST (feel-scala's variable references), not the raw text,
 * so it follows the grammar the engine evaluates. What this implies:
 *
 * <ul>
 *   <li>only expressions are scanned; a static value (no leading {@code =}) is a plain string;
 *   <li>a reference inside a string literal (e.g. {@code ="camunda.secrets.token"}) stays literal,
 *       so a runtime value that merely looks like a reference is never resolved (injection-safe);
 *   <li>FEEL variants — whitespace, unicode, backtick names, comments — are handled by feel-scala;
 *       a trailing path access into the secret ({@code camunda.secrets.token.length}) is a longer
 *       qualified name and is deliberately not treated as a reference.
 * </ul>
 *
 * <p>Known gaps (an undetected or less-specific reference is simply not resolved, so nothing
 * leaks): a {@code camunda} bound by an iterator/parameter/context key still reports the reference;
 * and a reference inside a context produced by a non-context expression (e.g. {@code =if c then {x:
 * camunda.secrets.token} else null}) is reported at the enclosing path, not the inner {@code x}.
 */
@NullMarked
public record SecretReference(String storeId, String name) {

  private static final String ROOT = "camunda";
  private static final String NAMESPACE = "secrets";

  /** Segments a {@code camunda.secrets.<name>} reference has: root, namespace and name. */
  private static final int REFERENCE_SEGMENT_COUNT = 3;

  /**
   * Creates a reference with no store id. The {@code camunda.secrets.<name>} syntax carries no
   * store dimension, so the store is left empty until store selection is wired to the engine
   * (tracked under the Secret Resolution epic, <a
   * href="https://github.com/camunda/camunda/issues/56563">#56563</a>).
   */
  public SecretReference(final String name) {
    this("", name);
  }

  /**
   * Parses the secret references used as expressions in a mapping source, each with the FEEL
   * context path where it occurs ({@code ="Bearer " + camunda.secrets.token} → one reference at the
   * empty path). Empty when the source is not a FEEL expression or holds no reference.
   */
  public static List<DetectedSecret> parse(@Nullable final Expression expression) {
    if (!(expression instanceof final FeelExpression feelExpression)) {
      // static values, null sources, and invalid expressions never contain references
      return List.of();
    }
    final var secrets = new ArrayList<DetectedSecret>();
    collect(feelExpression.getParsedExpression().expression(), new ArrayDeque<>(), secrets);
    return secrets;
  }

  /**
   * Walks a FEEL AST node, recording each secret reference with its enclosing context path. A
   * context is descended key by key; any other node is handed to feel-scala for its variable
   * references (which already exclude literals, comments and bound names).
   */
  private static void collect(
      final Exp node, final Deque<String> path, final List<DetectedSecret> secrets) {
    if (node instanceof final ConstContext context) {
      // keep track of nested paths
      // foo -> {x: camunda.secrets.x} will have path ["foo", "x"]
      for (final Tuple2<String, Exp> entry : CollectionConverters.asJava(context.entries())) {
        path.addLast(entry._1());
        collect(entry._2(), path, secrets);
        path.removeLast();
      }
      return;
    }
    for (final var reference : new ParsedExpression(node, "").getVariableReferences()) {
      final var qualifiedName = reference.getFullQualifiedName();
      if (isSecretReference(qualifiedName)) {
        final var secret = new SecretReference(qualifiedName.get(2));
        secrets.add(new DetectedSecret(List.copyOf(path), secret));
      }
    }
  }

  private static boolean isSecretReference(final List<String> qualifiedName) {
    // exactly three segments: a trailing path access (camunda.secrets.token.length) parses to a
    // longer qualified name and is deliberately not treated as a reference
    return qualifiedName.size() == REFERENCE_SEGMENT_COUNT
        && ROOT.equals(qualifiedName.get(0))
        && NAMESPACE.equals(qualifiedName.get(1));
  }

  /** The full reference as it appears in an expression, e.g. {@code camunda.secrets.token}. */
  public String reference() {
    return ROOT + "." + NAMESPACE + "." + name;
  }

  /**
   * A secret reference with the FEEL context path where it occurs. {@code [x]} for {@code ={x:
   * camunda.secrets.token}}. The path is appended to the mapping target to form the secret leaf's
   * JSON pointer.
   */
  public record DetectedSecret(List<String> path, SecretReference secret) {}
}
