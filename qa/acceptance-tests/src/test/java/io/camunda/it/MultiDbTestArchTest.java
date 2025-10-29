/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.qa.util.multidb.MultiDbTest;

@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.OnlyIncludeTests.class)
final class MultiDbTestArchTest {

  /**
   * This test ensures that PRs cannot be merged if you have committed a {@link MultiDbTest} with
   * explicit database types specified. Specifying an explicit database type is only intended for
   * local testing purposes, but should not be part of the code base.
   */
  @ArchTest
  static final ArchRule FORBID_MULTI_DB_SPECIFIED_DB =
      classes()
          .that()
          .areAnnotatedWith(MultiDbTest.class)
          .should()
          .beAnnotatedWith(
              DescribedPredicate.describe(
                  "@MultiDbTest(value = ...)",
                  annotation -> !annotation.hasExplicitlyDeclaredProperty("value")))
          .as(
              """
          @MultiDbTest should not specify explicit databases; that's only for local testing \
          purposes, please remove it before checking the code in""");
}
