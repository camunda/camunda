/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.secretstore.SecretStoreRegistry;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.impl.FeelExpression;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import org.camunda.feel.syntaxtree.ConstContext;
import org.camunda.feel.syntaxtree.ConstList;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.If;
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
 * <p>A name carrying a character FEEL does not allow in a bare identifier — a dash above all — has
 * to be backtick-escaped by the author: {@code =camunda.secrets.`db-password`}. Written bare,
 * {@code =camunda.secrets.db-password} is a subtraction to FEEL, so it reports a reference named
 * {@code db} and then fails at evaluation.
 *
 * <p>Known gaps (an undetected or less-specific reference is simply not resolved, so nothing
 * leaks): a {@code camunda} bound by an iterator/parameter/context key still reports the reference;
 * a reference inside a FEEL list literal (e.g. {@code =[camunda.secrets.token]}) is reported at the
 * enclosing path, not per element; a reference inside a context produced by a non-context
 * expression (e.g. {@code =if c then {x: camunda.secrets.token} else null}) is reported at the
 * enclosing path, not the inner {@code x}; and, conversely, a reference inside a nested condition
 * within such a container literal (e.g. {@code =if c then {x: if camunda.secrets.flag = "on" then 1
 * else 2} else null}) is rejected at deploy time even though no placeholder ever lands there,
 * because {@link #hasImpreciseReference} scans the whole container subtree rather than each
 * reference's own position - fixing that needs {@code collect()} itself to track position, which is
 * a larger refactor left for later.
 */
@NullMarked
public record SecretReference(String storeId, String name) {

  private static final String ROOT = "camunda";
  private static final String NAMESPACE = "secrets";

  /**
   * The text every reference starts with, {@code camunda.secrets.}. The single definition of the
   * prefix: callers that build a reference's text, strip it off a match, or match it in raw text
   * all derive it from here.
   */
  public static final String PREFIX = ROOT + "." + NAMESPACE + ".";

  /**
   * Matches a {@code camunda.secrets.<name>} occurrence in raw text, shared by callers that scan
   * text rather than a parsed FEEL AST (e.g. {@link
   * io.camunda.zeebe.engine.processing.deployment.model.validation.SecretReferenceLiteralValidator}
   * and {@link
   * io.camunda.zeebe.engine.processing.clustervariable.ClusterVariableSecretReferenceScanner}).
   *
   * <p>The name segment is ASCII alphanumerics, {@code _} and {@code -}. The dash is in because the
   * backing stores routinely hold dashed names — a Kubernetes secret data key is {@code
   * [-._a-zA-Z0-9]+}, a GCP secret id {@code [a-zA-Z0-9_-]+}. It is not anchored: a leading or
   * trailing dash is a legal store name too, and rejecting one here would move the
   * store-versus-reference mismatch rather than close it.
   *
   * <p>Narrower than what {@link #parse} accepts, which applies no charset check at all and takes
   * whatever feel-scala reports as an identifier — unicode letters, {@code $}, and anything at all
   * once backtick-escaped, {@code =camunda.secrets.`tls.crt`} included. Those names are detected
   * and resolved here while the {@code /v2/secrets} endpoints reject them. A dot cannot join this
   * charset without this raw-text pattern swallowing a trailing path access ({@code
   * camunda.secrets.token.length}), and a non-ASCII name first needs a decision on how it is
   * normalized before it becomes an authorization resource id.
   */
  public static final Pattern REFERENCE_PATTERN =
      Pattern.compile(Pattern.quote(PREFIX) + "[\\p{Alnum}_-]+");

  /** Segments a {@code camunda.secrets.<name>} reference has: root, namespace and name. */
  private static final int REFERENCE_SEGMENT_COUNT = 3;

  /**
   * Creates a reference to the default store. The {@code camunda.secrets.<name>} syntax carries no
   * store dimension, so it addresses {@link SecretStoreRegistry#DEFAULT_STORE_ID}; once store
   * selection is wired to the engine (tracked under the Secret Resolution epic, <a
   * href="https://github.com/camunda/camunda/issues/56563">#56563</a>), {@code camunda.secrets.X}
   * keeps meaning {@code camunda.secrets.default.X}.
   */
  public SecretReference(final String name) {
    this(SecretStoreRegistry.DEFAULT_STORE_ID, name);
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
   * True when the expression contains a {@code camunda.secrets.<name>} reference that {@link
   * #parse} can only report at an enclosing path, not the reference's own leaf: inside a FEEL list
   * literal, or inside a context literal produced by a branch of the expression rather than being
   * its own root (see this class's "Known gaps" above). Used to reject such a mapping at deploy
   * time instead of letting every instance of the element whose evaluated expression produces that
   * container hit the same {@code SECRET_RESOLUTION_ERROR} incident - unconditionally for a list
   * literal, or only the instances that take the producing branch for a conditional context.
   *
   * <p>This does not attempt to cover every way a FEEL expression could produce a container value
   * (e.g. a function call, a {@code for...return}) - only the two shapes above. Anything else stays
   * undetected, same as the rest of this class's documented limitations; JobSecretInjector's
   * runtime guard is the safety net for those.
   */
  public static boolean hasImpreciseReference(@Nullable final Expression expression) {
    if (!(expression instanceof final FeelExpression feelExpression)) {
      return false;
    }
    return isImprecise(feelExpression.getParsedExpression().expression());
  }

  private static boolean isImprecise(final Exp node) {
    if (node instanceof final ConstContext context) {
      // collect() descends this precisely, entry by entry; the same question applies one level
      // deeper for each entry, since an entry can itself hide a container reached only through an
      // If branch or a list
      return CollectionConverters.asJava(context.entries()).stream()
          .anyMatch(entry -> isImprecise(entry._2()));
    }
    // collect() would use the getVariableReferences() fallback over the whole node here, which
    // flattens the subtree onto the current path - a reference is imprecise exactly when it sits
    // inside a container literal within the subtree; one not under any container contributes a
    // scalar and stays leaf-precise
    return containsReferenceInsideContainerLiteral(node);
  }

  /**
   * True when a secret reference sits inside a {@link ConstList} or {@link ConstContext} literal
   * that {@link #isImprecise} does not reach by descending {@code ConstContext} entries key by key
   * - i.e. the literal is this node itself, or one reached through an {@link If} branch. A {@link
   * ConstContext} is treated differently depending on how it's reached: {@link #isImprecise}
   * descends its own entries key by key (precise, mirroring {@link #collect}), but reached any
   * other way, {@code collect()} never descends into it and every reference inside lands at the
   * enclosing path (imprecise).
   */
  private static boolean containsReferenceInsideContainerLiteral(final Exp node) {
    if (node instanceof ConstList || node instanceof ConstContext) {
      return containsReference(node);
    }
    if (node instanceof final If ifExp) {
      return containsReferenceInsideContainerLiteral(ifExp.statement())
          || containsReferenceInsideContainerLiteral(ifExp.elseStatement());
    }
    return false;
  }

  private static boolean containsReference(final Exp node) {
    for (final var reference : new ParsedExpression(node, "").getVariableReferences()) {
      if (isSecretReference(reference.getFullQualifiedName())) {
        return true;
      }
    }
    return false;
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
    return PREFIX + name;
  }

  /**
   * A secret reference with the FEEL context path where it occurs. {@code [x]} for {@code ={x:
   * camunda.secrets.token}}. The path is appended to the mapping target to form the secret leaf's
   * JSON pointer.
   */
  public record DetectedSecret(List<String> path, SecretReference secret) {}
}
