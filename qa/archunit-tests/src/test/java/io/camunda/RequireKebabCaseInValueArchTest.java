/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;

/**
 * ArchUnit rules around Spring's {@link Value} annotation to ensure property keys use kebab-case
 * for consistent cross-source resolution via Spring Boot's relaxed binding.
 *
 * <p>Spring Boot's relaxed binding for {@link Value} only applies when property keys use canonical
 * kebab-case. Non-canonical styles (camelCase, snake_case, UPPER_SNAKE_CASE) result in partial or
 * no cross-source resolution, causing silent misconfiguration when properties are supplied via
 * environment variables or application.properties using a different format.
 *
 * <p>These rules ensure that all {@link Value} annotations in production code use kebab-case
 * property keys, rejecting any key that contains uppercase letters or underscores.
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public final class RequireKebabCaseInValueArchTest {

  /**
   * Matches any property key that contains at least one uppercase letter or underscore. This
   * rejects camelCase, snake_case, and UPPER_SNAKE_CASE, while allowing only kebab-case.
   */
  static final Pattern FORBIDDEN_CHARS = Pattern.compile(".*[A-Z_].*");

  /**
   * Property keys exempted from kebab-case enforcement.
   *
   * <p>Use this whitelist only for genuine edge cases where renaming would introduce a
   * backward-incompatible change to a public-facing interface. The most common valid case is CLI
   * argument names: Spring resolves command-line arguments by exact match, so kebab-case provides
   * no cross-source binding benefit and renaming breaks existing callers.
   *
   * <p>Each entry must have a comment explaining the context and the risk of renaming.
   */
  static final Set<String> WHITELISTED_PROPERTY_KEYS =
      Set.of(
          // CLI arg of the Zeebe restore tool (dist/~/zeebe/restore/RestoreApp.java).
          // Passed as "--backupId=<id>" by the Docker entrypoint (zeebe/docker/utils/startup.sh)
          // and the Helm chart startup script (camunda-platform-helm):
          // -> charts/camunda-platform-8.X/templates/orchestration/configmap.yaml.
          // Renaming to "--backup-id" would introduce a backward compatibility risk:
          // operators using custom scripts invoking the restore tool directly with --backupId=<id>,
          // or users pinning to a specific image version while running a newer restore binary,
          // would silently get backupId=null — no error is thrown, restore runs without a target
          // backup ID.
          "backupId");

  @ArchTest
  static final ArchRule REQUIRE_KEBAB_CASE_IN_VALUE_ON_FIELDS =
      ArchRuleDefinition.fields()
          .that()
          .areAnnotatedWith(Value.class)
          .should(
              new ArchCondition<>("expect property keys in @Value to use kebab-case") {
                @Override
                public void check(final JavaField field, final ConditionEvents events) {
                  for (final JavaAnnotation<?> annotation : field.getAnnotations()) {
                    if (annotation.getRawType().isEquivalentTo(Value.class)) {
                      validateValueAnnotation(annotation.as(Value.class), field, events);
                    }
                  }
                }
              });

  @ArchTest
  static final ArchRule REQUIRE_KEBAB_CASE_IN_VALUE_ON_METHOD_PARAMETERS =
      ArchRuleDefinition.methods()
          .should(
              new ArchCondition<>(
                  "expect property keys in @Value on method parameters to use kebab-case") {
                @Override
                public void check(final JavaMethod method, final ConditionEvents events) {
                  for (final JavaParameter parameter : method.getParameters()) {
                    for (final JavaAnnotation<?> annotation : parameter.getAnnotations()) {
                      if (annotation.getRawType().isEquivalentTo(Value.class)) {
                        validateValueAnnotation(annotation.as(Value.class), method, events);
                      }
                    }
                  }
                }
              });

  @ArchTest
  static final ArchRule REQUIRE_KEBAB_CASE_IN_VALUE_ON_CONSTRUCTOR_PARAMETERS =
      ArchRuleDefinition.constructors()
          .should(
              new ArchCondition<>(
                  "expect property keys in @Value on constructor parameters to use kebab-case") {
                @Override
                public void check(final JavaConstructor constructor, final ConditionEvents events) {
                  for (final JavaParameter parameter : constructor.getParameters()) {
                    for (final JavaAnnotation<?> annotation : parameter.getAnnotations()) {
                      if (annotation.getRawType().isEquivalentTo(Value.class)) {
                        validateValueAnnotation(annotation.as(Value.class), constructor, events);
                      }
                    }
                  }
                }
              });

  /**
   * Validates that the property key extracted from a {@link Value} annotation does not contain
   * {@link RequireKebabCaseInValueArchTest#FORBIDDEN_CHARS}.
   *
   * @param valueAnnotation the Value annotation to validate
   * @param owner the element that owns the annotation (field, method, or constructor)
   * @param events collection of events to which violations are added
   */
  private static void validateValueAnnotation(
      final Value valueAnnotation, final HasName.AndFullName owner, final ConditionEvents events) {
    if (valueAnnotation == null) {
      return;
    }

    extractPropertyKey(valueAnnotation.value())
        .filter(Predicate.not(WHITELISTED_PROPERTY_KEYS::contains))
        .filter(RequireKebabCaseInValueArchTest::valueContainsForbiddenCharacters)
        .ifPresent(propertyKey -> events.add(addViolation(owner, propertyKey)));
  }

  /**
   * Extracts the property key from a Spring @Value expression. Handles expressions like: {@code
   * ${property.key}}, {@code ${property.key:defaultValue}}, {@code ${property.key:#{true}}}
   *
   * @param expression the full @Value expression string
   * @return Optional containing the property key, or empty if no key can be extracted
   */
  private static Optional<String> extractPropertyKey(final String expression) {
    if (expression == null || !expression.contains("${")) {
      return Optional.empty();
    }
    final int start = expression.indexOf("${") + 2;
    final int colonPos = expression.indexOf(':', start);
    final int bracePos = expression.indexOf('}', start);
    if (bracePos < 0) {
      return Optional.empty();
    }
    final int end = (colonPos >= 0 && colonPos < bracePos) ? colonPos : bracePos;
    return Optional.of(expression.substring(start, end));
  }

  private static boolean valueContainsForbiddenCharacters(final String value) {
    return value != null && FORBIDDEN_CHARS.matcher(value).matches();
  }

  private static ConditionEvent addViolation(
      final HasName.AndFullName element, final String propertyKey) {
    final String message =
        String.format(
            """
            @Value on '%s' has invalid property key: '%s' (contains uppercase or underscore). \
            Please use kebab-case instead.""",
            element.getFullName(), propertyKey);
    return SimpleConditionEvent.violated(element, message);
  }
}
