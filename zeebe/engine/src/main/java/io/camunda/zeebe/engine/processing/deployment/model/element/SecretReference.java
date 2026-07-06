/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A reference to a secret using the {@code camunda.secrets.<name>} format.
 *
 * <p>A reference is only recognized when it is used as a FEEL <em>expression</em> and outside of
 * string literals:
 *
 * <ul>
 *   <li>the source must be a FEEL expression, i.e. start with the {@code =} marker. A source that
 *       does not start with {@code =} is a static literal and never contains references.
 *   <li>a reference that appears inside a FEEL string literal (e.g. {@code
 *       ="camunda.secrets.token"}) is treated as plain text and is not returned. This prevents
 *       secret injection where a runtime value that happens to look like a reference would be
 *       replaced with a secret value.
 * </ul>
 *
 * <p>A single source may contain multiple references; each distinct reference is returned once.
 */
@NullMarked
public record SecretReference(String name) {

  /** The FEEL expression marker; a source is only an expression when it starts with this. */
  private static final String EXPRESSION_MARKER = "=";

  /** The prefix that identifies a secret reference. */
  private static final String PREFIX = "camunda.secrets.";

  /**
   * Finds secret references in a FEEL source. The pattern has two alternatives:
   *
   * <ol>
   *   <li>a whole string literal (e.g. {@code "some text"}) — matched but not captured, so a
   *       reference written inside quotes is treated as plain text and ignored;
   *   <li>a real {@code camunda.secrets.<name>} reference — captured in group 1.
   * </ol>
   *
   * <p>The string-literal alternative is first, so anything inside quotes is consumed before the
   * reference alternative can see it. The leading {@code (?<![\w.])} makes sure {@code camunda} is
   * a standalone root variable and not glued to something else. {@code <name>} is a FEEL
   * identifier.
   *
   * <p>Examples (source → captured):
   *
   * <ul>
   *   <li>{@code =camunda.secrets.token} → {@code token}
   *   <li>{@code ="camunda.secrets.token"} → nothing (inside a string literal)
   *   <li>{@code =xcamunda.secrets.token} → nothing (part of a longer identifier)
   *   <li>{@code =order.camunda.secrets.token} → nothing (nested property, not the root)
   * </ul>
   */
  private static final Pattern REFERENCE_PATTERN =
      Pattern.compile(
          "\"(?:\\\\.|[^\"\\\\])*\""
              + "|(?<![\\w.])"
              + Pattern.quote(PREFIX)
              + "([a-zA-Z_][a-zA-Z0-9_]*)");

  /**
   * Parses all secret references used as expressions in a variable-mapping source.
   *
   * @param source the raw source of an input mapping, e.g. {@code ="Bearer " +
   *     camunda.secrets.token}
   * @return the distinct secret references, or an empty set if the source is not an expression or
   *     contains no references outside string literals
   */
  public static Set<SecretReference> parse(@Nullable final String source) {
    if (source == null || !source.startsWith(EXPRESSION_MARKER)) {
      // not a FEEL expression -> the whole value is a literal, so there are no references
      return Set.of();
    }

    final var references = new LinkedHashSet<SecretReference>();
    final Matcher matcher = REFERENCE_PATTERN.matcher(source);
    while (matcher.find()) {
      // group 1 is null when the match is a string literal, which must be ignored
      final var name = matcher.group(1);
      if (name != null) {
        references.add(new SecretReference(name));
      }
    }
    return references;
  }

  /** The full reference as it appears in an expression, e.g. {@code camunda.secrets.token}. */
  public String reference() {
    return PREFIX + name;
  }
}
