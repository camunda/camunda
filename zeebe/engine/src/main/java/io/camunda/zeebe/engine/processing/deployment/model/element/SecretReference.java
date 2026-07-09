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
 * A reference to a secret using the {@code camunda.secrets.<name>} format.
 *
 * <p>References are detected from the parsed FEEL abstract syntax tree, not by matching the raw
 * source text, so detection follows the exact grammar the engine evaluates. The variable references
 * within an expression are extracted by feel-scala ({@link
 * ParsedExpression#getVariableReferences()} — the same mechanism used for variable-dependency
 * tracking); a reference is a secret when its fully-qualified name starts with {@code
 * camunda.secrets}. This means:
 *
 * <ul>
 *   <li>only FEEL <em>expressions</em> are scanned; a static value (a source that does not start
 *       with the {@code =} marker) is parsed as a plain string and never contains references;
 *   <li>a reference inside a FEEL string literal (e.g. {@code ="camunda.secrets.token"}) is a
 *       string constant, not a variable reference, so it stays literal &mdash; this prevents secret
 *       injection where a runtime value that looks like a reference would be resolved;
 *   <li>whitespace and line breaks around the dots ({@code camunda . secrets . token}), unicode
 *       names ({@code camunda.secrets.tokén}), backtick-escaped names ({@code
 *       camunda.secrets.`my-secret`}), trailing path access ({@code camunda.secrets.token.length})
 *       and FEEL comments are all handled by feel-scala.
 * </ul>
 *
 * <p>To know <em>where</em> a reference sits (for the JSON pointer of the value it produces), the
 * enclosing FEEL context is walked here: each context entry is descended and its key recorded as a
 * path segment. Known limitations &mdash; a reference that is not detected is simply not resolved,
 * so nothing leaks:
 *
 * <ul>
 *   <li>root shadowing is not accounted for: a bound name equal to {@code camunda} (a {@code for},
 *       {@code some} or {@code every} iterator, a function parameter, or a context key, e.g. {@code
 *       for camunda in [...] return camunda.secrets.token}) does not suppress the reference, so it
 *       is still reported &mdash; feel-scala only suppresses whole-name matches, not qualified
 *       paths rooted at the bound name;
 *   <li>a context nested inside a non-context expression (e.g. {@code =if c then {x:
 *       camunda.secrets.token} else null}) is detected but reported at the enclosing path, not at
 *       {@code x};
 *   <li>forms that do not parse to a plain {@code camunda.secrets.<name>} path, e.g. {@code
 *       (camunda).secrets.token} or {@code get value(camunda.secrets, "token")}, are not detected.
 * </ul>
 */
@NullMarked
public record SecretReference(String name) {

  /** The root variable a reference must be anchored to. */
  private static final String ROOT = "camunda";

  /** The namespace segment that follows the root. */
  private static final String NAMESPACE = "secrets";

  /** The number of leading path segments a {@code camunda.secrets.<name>} reference needs. */
  private static final int MINIMUM_SEGMENTS = 3;

  /**
   * Parses all secret references used as expressions in a variable-mapping source.
   *
   * @param expression the parsed source of an input mapping, e.g. {@code ="Bearer " +
   *     camunda.secrets.token}
   * @return each detected reference together with the context path (the keys of any enclosing FEEL
   *     context) at which it occurs; empty when the source is not a FEEL expression or contains no
   *     references
   */
  public static List<Located> parse(@Nullable final Expression expression) {
    if (!(expression instanceof final FeelExpression feelExpression)) {
      // static values, null sources, and invalid expressions never contain references
      return List.of();
    }
    final var located = new ArrayList<Located>();
    collect(feelExpression.getParsedExpression().expression(), new ArrayDeque<>(), located);
    return located;
  }

  /**
   * Collects secret references from a FEEL AST node. A context is descended key by key so each
   * reference keeps the path where it occurs; any other expression is handed to feel-scala, which
   * extracts its variable references (excluding literals, comments and shadowed names).
   */
  private static void collect(
      final Exp node, final Deque<String> contextPath, final List<Located> out) {
    if (node instanceof final ConstContext context) {
      for (final Tuple2<String, Exp> entry : CollectionConverters.asJava(context.entries())) {
        contextPath.addLast(entry._1());
        collect(entry._2(), contextPath, out);
        contextPath.removeLast();
      }
      return;
    }
    for (final var reference : new ParsedExpression(node, "").getVariableReferences()) {
      final var qualifiedName = reference.getFullQualifiedName();
      if (qualifiedName.size() >= MINIMUM_SEGMENTS
          && ROOT.equals(qualifiedName.get(0))
          && NAMESPACE.equals(qualifiedName.get(1))) {
        out.add(new Located(List.copyOf(contextPath), new SecretReference(qualifiedName.get(2))));
      }
    }
  }

  /** The full reference as it appears in an expression, e.g. {@code camunda.secrets.token}. */
  public String reference() {
    return ROOT + "." + NAMESPACE + "." + name;
  }

  /**
   * A secret reference together with the context path where it occurs within a mapping source. The
   * path holds the keys of any enclosing FEEL context, so it is empty for a scalar source (e.g.
   * {@code ="Bearer " + camunda.secrets.token}) and {@code [x2]} for a reference nested in a
   * context (e.g. {@code ={x2: camunda.secrets.token}}). It is later appended to the mapping target
   * to form the JSON pointer of the leaf the secret value belongs to.
   */
  public record Located(List<String> path, SecretReference reference) {}
}
