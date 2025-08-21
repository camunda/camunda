/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.archunit;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
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
import java.util.Collection;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * ArchUnit rules around Spring's {@link ConditionalOnProperty} to ensure we support both kebab-case
 * and camelCase segments in properties, by requiring kebab-case in the attributes of
 * ConditionalOnProperty.
 *
 * <p>ConditionalOnProperty attributes require kebab-case to ensure relaxed binding can be applied.
 * If camelCase is used in the attributes of ConditionalOnProperty, kebab-case configured properties
 * won't match the conditional's attributes. Vice versa that is not a problem, therefore spring-boot
 * is clear about requiring kebab-case in the ConditionalOnProperty javadoc. These rules ensure that
 * we do not accidentally use camelCase in the attributes of this annotation.
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public final class RequireKebabCaseInConditionalOnPropertyArchTest {

  /** Matches any string that contains at least one uppercase character. */
  static final Pattern FORBIDDEN_CHARS = Pattern.compile(".*([A-Z]).*");

  @ArchTest
  static final ArchRule REQUIRE_KEBAB_CASE_IN_CONDITIONAL_ON_PROPERTY_ON_TYPES =
      ArchRuleDefinition.classes()
          .that()
          .areAnnotatedWith(ConditionalOnProperty.class)
          .should(
              new ArchCondition<>("Expect attributes of @ConditionalOnProperty to use kebab-case") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  validateConditionalOnPropertyAnnotations(item.getAnnotations(), item, events);
                }
              });

  @ArchTest
  static final ArchRule REQUIRE_KEBAB_CASE_IN_CONDITIONAL_ON_PROPERTY_ON_METHODS =
      ArchRuleDefinition.methods()
          .that()
          .areAnnotatedWith(ConditionalOnProperty.class)
          .should(
              new ArchCondition<>("Expect attributes of @ConditionalOnProperty to use kebab-case") {
                @Override
                public void check(final JavaMethod item, final ConditionEvents events) {
                  validateConditionalOnPropertyAnnotations(item.getAnnotations(), item, events);
                }
              });

  /**
   * Validates that the attributes of {@link ConditionalOnProperty} annotations do not contain
   * forbidden characters (uppercase). ConditionalOnProperty requires all lower case.
   *
   * @param annotations the annotations to validate, e.g. from a class or method
   * @param owner the element that owns the annotations, e.g. a class or method
   * @param events collection of events to which violations are added
   */
  private static void validateConditionalOnPropertyAnnotations(
      final Collection<? extends JavaAnnotation<?>> annotations,
      final HasName.AndFullName owner,
      final ConditionEvents events) {
    for (final JavaAnnotation<?> ann : annotations) {
      if (!ann.getRawType().isEquivalentTo(ConditionalOnProperty.class)) {
        continue;
      }

      final ConditionalOnProperty cop = ann.as(ConditionalOnProperty.class);

      // prefix
      final String prefix = cop.prefix();
      if (valueContainsForbiddenCharacters(prefix)) {
        events.add(addViolation(owner, "prefix", prefix));
      }

      // name
      for (final String name : cop.name()) {
        if (valueContainsForbiddenCharacters(name)) {
          events.add(addViolation(owner, "name", name));
        }
      }

      // havingValue
      final String havingValue = cop.havingValue();
      if (valueContainsForbiddenCharacters(havingValue)) {
        events.add(addViolation(owner, "havingValue", havingValue));
      }

      // value
      for (final String value : cop.value()) {
        if (valueContainsForbiddenCharacters(value)) {
          events.add(addViolation(owner, "value", value));
        }
      }
    }
  }

  private static boolean valueContainsForbiddenCharacters(final String value) {
    return value != null && FORBIDDEN_CHARS.matcher(value).matches();
  }

  private static ConditionEvent addViolation(
      final HasName.AndFullName element, final String attribute, final String rawValue) {
    final String message =
        String.format(
            """
            @ConditionalOnProperty on '%s' has invalid %s: '%s' (contains uppercase). \
            Please use kebab-case instead.""",
            element.getFullName(), attribute, rawValue);
    return SimpleConditionEvent.violated(element, message);
  }
}
