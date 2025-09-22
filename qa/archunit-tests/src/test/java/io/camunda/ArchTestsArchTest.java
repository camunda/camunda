/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * ArchUnit rules related to arch tests themselves.
 *
 * <p>We require arch tests to be named *ArchTest and be located in the qa/archunit-tests module.
 * Other tests should not be named *ArchTest. This ensures that we can easily identify arch tests by
 * name when running them in CI.
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.OnlyIncludeTests.class)
public final class ArchTestsArchTest {

  /** Matches any class name that ends with "ArchTest". */
  static final String NAMING_CONVENTION = ".*ArchTest$";

  static final String QA_MODULE_PATH = "qa/archunit-tests";

  @ArchTest
  static final ArchRule REQUIRE_ARCH_TESTS_TO_BE_IN_CLASSES_NAMED_ARCH_TEST =
      ArchRuleDefinition.fields()
          .that()
          .areAnnotatedWith(ArchTest.class)
          .should()
          .beDeclaredInClassesThat()
          .haveNameMatching(NAMING_CONVENTION)
          .because("Arch tests should only be declared in classes named *ArchTest");

  @ArchTest
  static final ArchRule REQUIRE_CLASSES_NAMED_ARCH_TEST_TO_ACTUALLY_BE_ARCH_TESTS =
      ArchRuleDefinition.classes()
          .that()
          .haveNameMatching(NAMING_CONVENTION)
          .should(
              new ArchCondition<>("contain at least one field annotated @ArchTest") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  if (item.getFields().stream()
                      .noneMatch(field -> field.isAnnotatedWith(ArchTest.class))) {
                    events.add(
                        violated(
                            item,
                            String.format(
                                "Class '%s' does not contain arch tests", item.getSimpleName())));
                  }
                }
              })
          .because("Classes named *ArchTest should actually contain arch tests");

  @ArchTest
  static final ArchRule REQUIRE_ARCH_TESTS_TO_BE_IN_THE_QA_MODULE =
      ArchRuleDefinition.classes()
          .that()
          .haveNameMatching(NAMING_CONVENTION)
          .should(
              new ArchCondition<>("belong to class named *ArchTest") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  final String classPath = item.getSource().get().getUri().getPath();
                  if (!classPath.contains(QA_MODULE_PATH)) {
                    events.add(
                        violated(
                            item,
                            String.format(
                                "Class '%s' located in '%s' instead of inside '%s'",
                                item.getSimpleName(), classPath, QA_MODULE_PATH)));
                  }
                }
              })
          .because("Arch tests should be located in the camunda-archunit-tests module");
}
