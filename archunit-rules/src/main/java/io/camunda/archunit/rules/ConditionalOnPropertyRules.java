/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.archunit.rules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Collection;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Shared ArchUnit rules around Spring's {@link ConditionalOnProperty} to prevent usage of camelCase
 * or kebab-case segments in attributes like prefix, name/value, or havingValue. These attributes
 * are matched literally by Spring's condition evaluation and do not benefit from relaxed binding.
 */
public final class ConditionalOnPropertyRules {

  private static final Pattern FORBIDDEN_CHARS = Pattern.compile(".*(-|[A-Z]).*");

  private ConditionalOnPropertyRules() {}

  public static ArchRule notUseCamelOrKebabInConditionalOnPropertyOnTypes() {
    return classes().should(notUseCamelOrKebabInConditionalOnPropertyForTypes());
  }

  public static ArchRule notUseCamelOrKebabInConditionalOnPropertyOnMethods() {
    return methods().should(notUseCamelOrKebabInConditionalOnPropertyForMethods());
  }

  public static ArchCondition<JavaClass> notUseCamelOrKebabInConditionalOnPropertyForTypes() {
    return new ArchCondition<>(
        "not use @ConditionalOnProperty with dashes or capital letters in attributes") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        validateAnnotations(item.getAnnotations(), item, events);
      }
    };
  }

  public static ArchCondition<JavaMethod> notUseCamelOrKebabInConditionalOnPropertyForMethods() {
    return new ArchCondition<>(
        "not use @ConditionalOnProperty with dashes or capital letters in attributes") {
      @Override
      public void check(final JavaMethod item, final ConditionEvents events) {
        validateAnnotations(item.getAnnotations(), item, events);
      }
    };
  }

  private static void validateAnnotations(
      final Collection<? extends JavaAnnotation<?>> annotations,
      final HasName owner,
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

  private static ConditionEvent addViolation(
      final HasName element, final String attribute, final String rawValue) {
    final String message =
        String.format(
            "@ConditionalOnProperty on '%s' has invalid %s: '%s' (contains dash or uppercase)",
            element.getName(), attribute, rawValue);
    return SimpleConditionEvent.violated(element, message);
  }

  private static boolean valueContainsForbiddenCharacters(final String value) {
    return value != null && FORBIDDEN_CHARS.matcher(value).matches();
  }
}
