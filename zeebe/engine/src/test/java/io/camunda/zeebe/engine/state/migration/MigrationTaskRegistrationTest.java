/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine.state.migration",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class MigrationTaskRegistrationTest {

  @ArchTest()
  public static final ArchRule MIGRATION_TASKS_MUST_BE_REGISTERED =
      classes()
          .that()
          .implement(MigrationTask.class)
          .should()
          .beAssignableFrom(anyOfTheRegisteredMigrationTasks());

  private static DescribedPredicate<JavaClass> anyOfTheRegisteredMigrationTasks() {
    return new DescribedPredicate<>("any of the migration tasks registered in the DbMigratorImpl") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return DbMigratorImpl.MIGRATION_TASKS.stream()
            .map(MigrationTask::getClass)
            .anyMatch(javaClass::isAssignableFrom);
      }
    };
  }
}
