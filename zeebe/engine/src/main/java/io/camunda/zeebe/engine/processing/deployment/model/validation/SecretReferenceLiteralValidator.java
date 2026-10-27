/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.util.LinkedHashSet;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Rejects a deployment when a {@code camunda.secrets.<name>} reference is used as a string literal
 * in a source the engine may resolve as an expression; only expression usage (a FEEL path) is
 * allowed. This removes the literal-vs-expression ambiguity, so a reference left in a valid source
 * is always an expression.
 *
 * <p>It applies uniformly to the two places a secret reference can be authored: a {@link
 * ZeebeInput} mapping source (outbound) and a {@link ZeebeProperty} value (inbound). Both are
 * validated by the same rule so the two forms behave identically. Use {@link
 * #forInput(ExpressionLanguage)} and {@link #forProperty(ExpressionLanguage)} to obtain the two
 * registrations.
 *
 * <p>Detection is purely static: a static value (no leading {@code =}) is scanned as a whole, and a
 * FEEL expression is scanned only inside its double-quoted string literals. A bare path such as
 * {@code =camunda.secrets.token} is never quoted and is allowed; a quoted occurrence, including one
 * nested in an object or list literal (e.g. {@code ={"auth": "camunda.secrets.token"}}), is
 * rejected. The source is never evaluated, so a pathological expression cannot break the
 * deployment.
 */
@NullMarked
final class SecretReferenceLiteralValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  // Matches one whole double-quoted string literal, so only quoted text is scanned for a reference.
  // As a regex (after Java unescaping): "[^"\]*+(?:\\.[^"\]*+)*+"
  //   "              opening double quote
  //   [^"\]*+        a possessive run of non-quote, non-backslash chars
  //   (?:\\.[^"\]*+)*+  zero or more: one escape (\\ + any char) then another safe run
  //   "              closing double quote
  // Unrolled + possessive: long safe runs avoid per-char alternation, and *+ / ++ prevent the
  // recursive group-loop StackOverflowError that greedy * hit on multi-kilobyte escaped FEEL
  // strings (e.g. embedded JSON with many \"). See #59121.
  // e.g. it matches "ab" and "a\"b".
  private static final Pattern STRING_LITERAL =
      Pattern.compile("\"[^\"\\\\]*+(?:\\\\.[^\"\\\\]*+)*+\"");

  private final Class<T> elementType;
  private final Function<T, @Nullable String> sourceExtractor;
  private final String location;
  private final ExpressionLanguage expressionLanguage;

  private SecretReferenceLiteralValidator(
      final Class<T> elementType,
      final Function<T, @Nullable String> sourceExtractor,
      final String location,
      final ExpressionLanguage expressionLanguage) {
    this.elementType = elementType;
    this.sourceExtractor = sourceExtractor;
    this.location = location;
    this.expressionLanguage = expressionLanguage;
  }

  /** Validates the source of an input mapping ({@code zeebe:input}). */
  static SecretReferenceLiteralValidator<ZeebeInput> forInput(
      final ExpressionLanguage expressionLanguage) {
    return new SecretReferenceLiteralValidator<>(
        ZeebeInput.class, ZeebeInput::getSource, "input mapping source", expressionLanguage);
  }

  /** Validates the value of a property ({@code zeebe:property}). */
  static SecretReferenceLiteralValidator<ZeebeProperty> forProperty(
      final ExpressionLanguage expressionLanguage) {
    return new SecretReferenceLiteralValidator<>(
        ZeebeProperty.class, ZeebeProperty::getValue, "property value", expressionLanguage);
  }

  @Override
  public Class<T> getElementType() {
    return elementType;
  }

  @Override
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {
    final String source = sourceExtractor.apply(element);
    if (source == null) {
      return;
    }

    final Expression expression = expressionLanguage.parseExpression(source);
    if (!expression.isValid()) {
      // invalid expressions are reported separately by the expression validator
      return;
    }

    // a static value is a literal in full; a FEEL expression is a literal only where it is quoted
    final String literalText = expression.isStatic() ? source : stringLiterals(source.substring(1));

    final var matcher = SecretReference.REFERENCE_PATTERN.matcher(literalText);
    final var references = new LinkedHashSet<String>();
    while (matcher.find()) {
      references.add(matcher.group());
    }
    if (!references.isEmpty()) {
      final var formatted =
          references.stream()
              .map(reference -> "'" + reference + "'")
              .collect(Collectors.joining(", "));
      validationResultCollector.addError(
          0,
          String.format(
              "Secret reference(s) %s must be used as an expression (e.g. '=camunda.secrets.<name>'),"
                  + " not as a string literal, in %s '%s'.",
              formatted, location, source));
    }
  }

  /**
   * Every double-quoted string literal in a FEEL expression body, joined by a separator so adjacent
   * literals cannot fuse into a spurious match. Text outside string literals (such as a {@code
   * camunda.secrets.token} path expression) is not matched, so only quoted usage is flagged.
   *
   * <p>Uses an unrolled possessive regex ({@link #STRING_LITERAL}) so multi-kilobyte escaped FEEL
   * strings cannot overflow the stream-processor stack. See #59121.
   */
  private static String stringLiterals(final String feelBody) {
    return STRING_LITERAL
        .matcher(feelBody)
        .results()
        .map(MatchResult::group)
        .collect(Collectors.joining("\n"));
  }
}
