/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ArchUnit rules that govern correct usage of the {@link RequiresSecondaryStorage} annotation in
 * gateway REST controllers.
 *
 * <p>This class is intentionally separate from {@link RestControllerAnnotationArchTest} to keep
 * {@link RequiresSecondaryStorage} annotation conventions co-located and distinct from the
 * gateway-enabled guarding rules enforced by that class.
 *
 * <p>Current rules:
 *
 * <ul>
 *   <li>{@link #RULE_HANDLER_MUST_NOT_REPEAT_CLASS_LEVEL_REQUIRES_SECONDARY_STORAGE_ANNOTATION} —
 *       methods must not carry {@link RequiresSecondaryStorage} when the enclosing class is already
 *       annotated with it.
 * </ul>
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.gateway.rest",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class RequiresSecondaryStorageAnnotationArchTest {

  /**
   * Methods annotated with {@link RequiresSecondaryStorage} must not reside in a class that is
   * itself annotated with {@link RequiresSecondaryStorage}.
   *
   * <p>Redundant dual annotation is a maintenance hazard: it is unclear whether the class-level
   * annotation is authoritative (and the method-level copy is noise), or whether the class-level
   * annotation should be removed and only specific methods should remain annotated.
   */
  @ArchTest
  public static final ArchRule
      RULE_HANDLER_MUST_NOT_REPEAT_CLASS_LEVEL_REQUIRES_SECONDARY_STORAGE_ANNOTATION =
          ArchRuleDefinition.classes()
              .that()
              .areAnnotatedWith(RequiresSecondaryStorage.class)
              .should(notHaveMethodsRedundantlyAnnotated())
              .because(
                  "redundant @RequiresSecondaryStorage at both class and method level is ambiguous;"
                      + " either keep only the class-level annotation (if all endpoints require"
                      + " secondary storage) or keep only the necessary method-level annotations");

  /**
   * For each class already annotated with {@link RequiresSecondaryStorage}, checks that none of its
   * methods are themselves annotated with {@link RequiresSecondaryStorage}.
   */
  private static ArchCondition<JavaClass> notHaveMethodsRedundantlyAnnotated() {
    return new ArchCondition<>(
        "not have methods redundantly annotated with @RequiresSecondaryStorage") {
      @Override
      public void check(final JavaClass clazz, final ConditionEvents events) {
        final List<JavaMethod> redundantMethods =
            clazz.getMethods().stream()
                .filter(method -> method.isAnnotatedWith(RequiresSecondaryStorage.class))
                .toList();
        if (!redundantMethods.isEmpty()) {
          events.add(SimpleConditionEvent.violated(clazz, buildMessage(clazz, redundantMethods)));
        }
      }
    };
  }

  private static String buildMessage(final JavaClass clazz, final List<JavaMethod> methods) {
    final String methodList =
        methods.stream()
            .map(m -> "  - " + m.getFullName().substring(clazz.getName().length() + 1))
            .collect(Collectors.joining("\n"));
    return String.format(
        """
        Redundant @RequiresSecondaryStorage placement detected.

        Controller : %s

        The following methods are annotated with @RequiresSecondaryStorage,
        but the enclosing class is already annotated with @RequiresSecondaryStorage:
        %s

        Choose one of the following fixes:
        1. If only SOME endpoints require secondary storage:
           - Remove the class-level @RequiresSecondaryStorage annotation from the controller
           - Keep @RequiresSecondaryStorage only on the specific handler methods that need it
        2. If ALL endpoints in the controller require secondary storage:
           - Keep the class-level @RequiresSecondaryStorage annotation on the controller
           - Remove the now-redundant method-level @RequiresSecondaryStorage annotation""",
        clazz.getName(), methodList);
  }
}
