/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards against test classes in {@code io.camunda.it} being named {@code *Test} instead of {@code
 * *IT}.
 *
 * <p>{@code qa/acceptance-tests} is executed via the maven-failsafe-plugin, which by default only
 * picks up classes matching {@code **&#47;*IT.java}. A class named {@code *Test.java} in this
 * package therefore silently never runs.
 */
@AnalyzeClasses(packages = "io.camunda.it", importOptions = ImportOption.OnlyIncludeTests.class)
public class NamingArchTest {

  private static final DescribedPredicate<JavaClass> ABSTRACT =
      new DescribedPredicate<>("abstract") {
        @Override
        public boolean test(final JavaClass javaClass) {
          return javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
        }
      };

  @ArchTest
  static final ArchRule RULE_NO_TEST_SUFFIXED_CLASSES_IN_IO_CAMUNDA_IT =
      noClasses()
          .that()
          .resideInAPackage("io.camunda.it..")
          .and()
          .areNotAnnotations()
          .and()
          .haveNameNotMatching(".*ArchTest$")
          .and(DescribedPredicate.not(ABSTRACT))
          .should()
          .haveSimpleNameEndingWith("Test")
          .because(
              "classes in io.camunda.it are only picked up by Failsafe when named *IT; "
                  + "a *Test class here silently never runs");
}
