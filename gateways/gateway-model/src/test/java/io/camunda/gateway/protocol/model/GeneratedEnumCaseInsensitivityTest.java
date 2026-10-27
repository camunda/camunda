/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.protocol.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Locks in the case-insensitive {@code fromValue(...)} contract emitted on every generated enum
 * under {@code io.camunda.gateway.protocol.model}. The behaviour was previously expressed via the
 * {@code <useEnumCaseInsensitive>true</useEnumCaseInsensitive>} openapi-generator config option;
 * after switching to a hand-rolled {@code ModelGenerator}, the contract lives only in the
 * generator's emitted {@code equalsIgnoreCase} matcher and would otherwise be easy to drop in a
 * future cleanup. This test asserts that for every {@code *Enum} class the generator produced,
 * looking up the first constant via {@code fromValue} works regardless of casing.
 *
 * <p>See PR #52353 (this PR) and #52607 (docs) for the documented contract.
 */
@DisplayName("Generated *Enum classes preserve case-insensitive fromValue(...) lookup")
class GeneratedEnumCaseInsensitivityTest {

  private static final String MODEL_PACKAGE = "io.camunda.gateway.protocol.model";

  @TestFactory
  Stream<DynamicTest> caseInsensitiveFromValueOnEveryGeneratedEnum() throws Exception {
    final List<Class<? extends Enum<?>>> enums = findGeneratedEnums();
    assertThat(enums)
        .as("expected at least one generated *Enum class on the classpath")
        .isNotEmpty();
    return enums.stream()
        .map(
            type ->
                DynamicTest.dynamicTest(
                    type.getSimpleName(), () -> assertCaseInsensitiveFromValue(type)));
  }

  private static void assertCaseInsensitiveFromValue(final Class<? extends Enum<?>> type)
      throws ReflectiveOperationException {
    final Object[] constants = type.getEnumConstants();
    if (constants.length == 0) {
      fail("Generated enum %s has no constants — generator regression?", type.getName());
    }
    final Method getValue = type.getMethod("getValue");
    final Method fromValue = type.getMethod("fromValue", String.class);
    final String canonical = (String) getValue.invoke(constants[0]);
    // skip if the canonical form has no letters to permute (e.g. "*", "<default>") — the
    // case-insensitivity contract is trivially satisfied
    if (canonical == null || canonical.chars().noneMatch(Character::isLetter)) {
      return;
    }

    final Object viaCanonical = invoke(fromValue, canonical);
    final Object viaLower = invoke(fromValue, canonical.toLowerCase());
    final Object viaUpper = invoke(fromValue, canonical.toUpperCase());
    final Object viaSwapped = invoke(fromValue, swapCase(canonical));

    assertThat(viaCanonical)
        .as("fromValue(\"%s\") on %s", canonical, type.getSimpleName())
        .isSameAs(constants[0]);
    assertThat(viaLower)
        .as("fromValue(\"%s\") (lowercase) on %s", canonical.toLowerCase(), type.getSimpleName())
        .isSameAs(constants[0]);
    assertThat(viaUpper)
        .as("fromValue(\"%s\") (uppercase) on %s", canonical.toUpperCase(), type.getSimpleName())
        .isSameAs(constants[0]);
    assertThat(viaSwapped)
        .as("fromValue(\"%s\") (swapped case) on %s", swapCase(canonical), type.getSimpleName())
        .isSameAs(constants[0]);
  }

  private static Object invoke(final Method m, final Object arg)
      throws IllegalAccessException, InvocationTargetException {
    return m.invoke(null, arg);
  }

  private static String swapCase(final String s) {
    final char[] out = s.toCharArray();
    for (int i = 0; i < out.length; i++) {
      final char c = out[i];
      if (Character.isLowerCase(c)) {
        out[i] = Character.toUpperCase(c);
      } else if (Character.isUpperCase(c)) {
        out[i] = Character.toLowerCase(c);
      }
    }
    return new String(out);
  }

  /**
   * Reflectively scans the model package on the classpath for {@code *Enum.class} files and loads
   * each one. Iterates every {@link ClassLoader#getResources resource} for the package because the
   * package directory exists in both {@code target/classes} (where the generated enum classes live)
   * and {@code target/test-classes} (where this test class lives), and we want the union. Avoids
   * pulling in a classpath-scanning dependency for a one-off test.
   */
  private static List<Class<? extends Enum<?>>> findGeneratedEnums()
      throws IOException, URISyntaxException, ClassNotFoundException {
    final var resources =
        GeneratedEnumCaseInsensitivityTest.class
            .getClassLoader()
            .getResources(MODEL_PACKAGE.replace('.', '/'));
    final List<Class<? extends Enum<?>>> found = new ArrayList<>();
    while (resources.hasMoreElements()) {
      final Path packageDir = Paths.get(resources.nextElement().toURI());
      try (final var paths = Files.list(packageDir)) {
        for (final Path p : paths.toList()) {
          final String fileName = p.getFileName().toString();
          if (!fileName.endsWith("Enum.class") || fileName.contains("$")) {
            continue;
          }
          final String simpleName = fileName.substring(0, fileName.length() - ".class".length());
          final Class<?> type = Class.forName(MODEL_PACKAGE + "." + simpleName);
          if (type.isEnum()) {
            @SuppressWarnings("unchecked")
            final Class<? extends Enum<?>> asEnum = (Class<? extends Enum<?>>) type;
            found.add(asEnum);
          }
        }
      }
    }
    return found;
  }
}
