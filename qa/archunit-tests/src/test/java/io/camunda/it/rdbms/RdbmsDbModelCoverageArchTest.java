/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms;

import static com.tngtech.archunit.lang.SimpleConditionEvent.satisfied;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.archunit.IncludeTestClassesAndTestJars;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Enforces that every RDBMS entity model ({@code XXXDbModel}) is covered by at least one
 * integration test ({@code XXXXIT}) in {@code io.camunda.it.rdbms.db}.
 *
 * <p>{@link AnalyzeClasses} scans only the production package (DbModel classes). IT class names are
 * pre-computed at class-load time via a second {@link ClassFileImporter} restricted to test-jars,
 * matching the dual-scan pattern used before this class was converted to the native ArchUnit
 * {@code @ArchTest} format required by CI.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.write.domain",
    importOptions = DoNotIncludeTestsOrTestJars.class)
class RdbmsDbModelCoverageArchTest {

  // Sub-entities and variant models that are not independently tested by their own IT.
  // They are covered by a parent entity's IT (see comment per entry).
  static final Set<String> NON_INDEPENDENT_DB_MODELS =
      Set.of(
          "UsageMetricTUDbModel", // TU variant of UsageMetricDbModel; covered by UsageMetricIT
          "UserTaskMigrationDbModel", // migration helper model; covered by UserTaskIT
          "BatchOperationItemDbModel", // sub-entity of BatchOperation; covered by BatchOperationIT
          "BatchOperationErrorDbModel", // error sub-entity of BatchOperation; covered by
          // BatchOperationIT
          "HistoryDeletionTypeDbModel", // type sub-model for HistoryDeletion; covered by
          // HistoryDeletionIT
          "EventTypeDbModel", // audit-event type enum model; no standalone IT
          "UsageMetricStatisticsDbModel", // statistics sub-model; covered by UsageMetricIT
          "UsageMetricTUStatisticsDbModel", // TU statistics variant; covered by UsageMetricIT
          "UsageMetricTenantStatisticsDbModel", // tenant statistics variant; covered by
          // UsageMetricIT
          "UsageMetricTUTenantStatisticsDbModel", // TU tenant statistics variant; covered by
          // UsageMetricIT
          // Write-path only; AgentHistoryIT will be added with the read-path (#55271)
          "AgentHistoryDbModel");

  // IT class simple names scanned from test-jars once at class-load time.
  static final Set<String> IT_SIMPLE_NAMES =
      StreamSupport.stream(
              new ClassFileImporter()
                  .withImportOption(new IncludeTestClassesAndTestJars())
                  .importPackages("io.camunda.it.rdbms.db")
                  .spliterator(),
              false)
          .filter(c -> c.getSimpleName().endsWith("IT"))
          .map(JavaClass::getSimpleName)
          .collect(Collectors.toUnmodifiableSet());

  @ArchTest
  static final ArchRule EACH_DB_MODEL_HAS_RDBMS_IT =
      ArchRuleDefinition.classes()
          .that()
          .haveSimpleNameEndingWith("DbModel")
          .should(haveCorrespondingItTest())
          .because(
              "each RDBMS entity model must be covered by an IT in io.camunda.it.rdbms.db.*"
                  + " to ensure it is tested against all databases");

  private static ArchCondition<JavaClass> haveCorrespondingItTest() {
    return new ArchCondition<>("have a corresponding IT in io.camunda.it.rdbms.db.*") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        if (javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
            || javaClass.isInterface()
            || NON_INDEPENDENT_DB_MODELS.contains(javaClass.getSimpleName())) {
          events.add(satisfied(javaClass, "skipped — not an independently tested entity"));
          return;
        }
        final String entityName = javaClass.getSimpleName().replace("DbModel", "");
        final String expectedIt = entityName + "IT";
        if (IT_SIMPLE_NAMES.contains(expectedIt)) {
          events.add(satisfied(javaClass, "found " + expectedIt));
        } else {
          events.add(
              violated(
                  javaClass,
                  javaClass.getSimpleName()
                      + " has no '"
                      + expectedIt
                      + "' in io.camunda.it.rdbms.db.*; add the missing IT or add the DbModel"
                      + " to NON_INDEPENDENT_DB_MODELS if it is not an independent entity"));
        }
      }
    };
  }
}
