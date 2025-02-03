/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.zeebe.util.VisibleForTesting;

@AnalyzeClasses(packages = "io.camunda.zeebe", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

  @ArchTest
  public static final ArchRule RULE_ENGINE_CLASSES_MUST_NOT_DEPEND_ON_STREAMPROCESSOR_PACKAGE =
      noClasses()
          .that()
          .resideInAPackage("io.camunda.zeebe.engine..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.camunda.zeebe.stream.impl..");

  @ArchTest
  public static final ArchRule RULE_ENGINE_CLASSES_MUST_NOT_DEPEND_ON_SCHEDULER_PACKAGE =
      noClasses()
          .that()
          .resideInAPackage("io.camunda.zeebe.engine..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.camunda.zeebe.scheduler..");

  private static final DescribedPredicate<? super JavaMethod> CALLED_WITHIN_CONTAINING_CLASS =
      new DescribedPredicate<>("Annotation") {

        @Override
        public boolean test(final JavaMethod javaMethod) {
          if (!javaMethod.isAnnotatedWith(VisibleForTesting.class)) {
            return true;
          }

          final JavaClass declaringClass = javaMethod.getOwner();

          // Allow calls if they are from within the same class
          return declaringClass.getMethodCallsFromSelf().stream()
              .anyMatch(call -> call.getTarget().equals(declaringClass));
        }
      };
  private static final DescribedPredicate<? super JavaMethod> CALLED_FROM_TEST_CLASS =
      new DescribedPredicate<>("Called from a test class") {
        @Override
        public boolean test(final JavaMethod method) {
          if (!method.isAnnotatedWith(VisibleForTesting.class)) {
            return true;
          }

          return method.getOwner().getDirectDependenciesFromSelf().stream()
              .anyMatch(dep -> dep.getTargetClass().getSimpleName().endsWith("Test"));
        }
      };

  @ArchTest
  public static final ArchRule RULE_ENGINE_CLASSES_VISIBLE_FOR_TESTING_METHODS_USAGE =
      classes()
          .that()
          .resideInAPackage("io.camunda.zeebe.engine..")
          .should()
          .onlyCallMethodsThat(CALLED_WITHIN_CONTAINING_CLASS)
          .orShould()
          .onlyCallMethodsThat(CALLED_FROM_TEST_CLASS);
}
