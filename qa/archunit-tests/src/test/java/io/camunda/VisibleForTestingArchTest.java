/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Set;

/**
 * ArchUnit rules to enforce correct usage of the {@link io.camunda.zeebe.util.VisibleForTesting}
 * annotation.
 *
 * <p>Elements (classes, methods, fields) annotated with {@code @VisibleForTesting} must only be
 * accessed from test code or from within the same class (self-usage). This prevents accidental
 * usage in production code outside their intended scope.
 *
 * @see io.camunda.zeebe.util.VisibleForTesting
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public class VisibleForTestingArchTest {

  @ArchTest
  static final ArchRule CLASS_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      classes()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should(beClassAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule METHOD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      methods()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should(beMethodAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule FIELD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      fields()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should(beFieldAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule CONSTRUCTOR_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      constructors()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should(beConstructorAccessedBySelfOrTest());

  private static ArchCondition<JavaClass> beClassAccessedBySelfOrTest() {
    return new ArchCondition<>("be accessed from test or self") {
      @Override
      public void check(final JavaClass element, final ConditionEvents events) {
        validateAccessViolations(
            events, element.getAccessesToSelf(), element, "Class " + element.getFullName());
      }
    };
  }

  private static ArchCondition<JavaMethod> beMethodAccessedBySelfOrTest() {
    return new ArchCondition<>("be accessed from test or self") {
      @Override
      public void check(final JavaMethod element, final ConditionEvents events) {
        validateAccessViolations(
            events,
            element.getAccessesToSelf(),
            element.getOwner(),
            "Method " + element.getFullName());
      }
    };
  }

  private static ArchCondition<JavaField> beFieldAccessedBySelfOrTest() {
    return new ArchCondition<>("be accessed from test or self") {
      @Override
      public void check(final JavaField element, final ConditionEvents events) {
        validateAccessViolations(
            events,
            element.getAccessesToSelf(),
            element.getOwner(),
            "Field " + element.getFullName());
      }
    };
  }

  private static ArchCondition<JavaConstructor> beConstructorAccessedBySelfOrTest() {
    return new ArchCondition<>("be accessed from test or self") {
      @Override
      public void check(final JavaConstructor element, final ConditionEvents events) {
        validateAccessViolations(
            events,
            element.getAccessesToSelf(),
            element.getOwner(),
            "Constructor " + element.getFullName());
      }
    };
  }

  private static void validateAccessViolations(
      final ConditionEvents events,
      final Set<? extends JavaAccess<?>> accessesToSelf,
      final JavaClass owner,
      final String displayName) {

    accessesToSelf.stream()
        .filter(access -> isViolation(owner, access.getOriginOwner()))
        .forEach(
            violationAccess ->
                events.add(
                    SimpleConditionEvent.violated(
                        violationAccess,
                        String.format(
                            "%s is accessed from %s, which is not a test but still requires extended visibility.",
                            displayName, violationAccess.getOriginOwner().getFullName()))));
  }

  private static boolean isViolation(final JavaClass javaOwner, final JavaClass javaOriginOwner) {
    return !isCalledBySelf(javaOwner, javaOriginOwner) && !isTestClass(javaOriginOwner);
  }

  private static boolean isTestClass(final JavaClass javaClass) {
    final var javaClassSource = javaClass.getSource();
    return javaClassSource.isPresent() && javaClassSource.get().toString().contains("test");
  }

  private static boolean isCalledBySelf(final JavaClass calledClass, final JavaClass callingClass) {
    // If the calling class is the same as the called class, it's a self-access
    // This also covers the case of nested classes, as they can access private members of their
    // enclosing class and vice versa
    return topLevelClass(callingClass).equals(topLevelClass(calledClass));
  }

  private static JavaClass topLevelClass(final JavaClass javaClass) {
    var topLevelClass = javaClass;
    while (topLevelClass.getEnclosingClass().isPresent()) {
      topLevelClass = topLevelClass.getEnclosingClass().get();
    }
    return topLevelClass;
  }
}
