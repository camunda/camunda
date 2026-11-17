/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.archunit;

import java.util.regex.Pattern;

/**
 * Shared character-set validation for Spring property keys, used by e.g. {@code
 * RequireKebabCaseInValueArchTest} and {@code RequireKebabCaseInConditionalOnPropertyArchTest} so
 * both rules enforce the exact same definition of canonical kebab-case and cannot drift apart.
 *
 * <p>Spring Boot's relaxed binding only reliably resolves property keys written in canonical
 * kebab-case: lowercase letters, digits, dots (for nesting), and hyphens (for word separation). Any
 * other character - uppercase letters, underscores, or special characters such as {@code !},
 * {@code @}, and so on - either breaks relaxed binding or, worse, is silently accepted: Spring
 * falls back to the annotation's default value with no error or warning, hiding a misconfigured
 * key.
 */
public final class KebabCasePropertyKeys {

  /** Property keys/attributes may only contain lowercase letters, digits, dots, and hyphens. */
  private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[a-z0-9.-]+");

  private KebabCasePropertyKeys() {}

  /**
   * Callers must not pass optional annotation attributes that default to an empty string (e.g.
   * {@code @ConditionalOnProperty}'s {@code prefix}) unless they were actually set; this method
   * rejects {@code null} and blank values instead of treating them as valid.
   *
   * @param value the property key or attribute to validate
   * @return {@code true} if {@code value} is not {@code null} and contains only lowercase letters,
   *     digits, dots, and hyphens
   */
  public static boolean isValid(final String value) {
    return value != null && ALLOWED_CHARACTERS.matcher(value).matches();
  }
}
