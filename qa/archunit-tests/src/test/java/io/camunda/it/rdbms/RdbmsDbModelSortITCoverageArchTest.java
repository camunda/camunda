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
 * Enforces that every RDBMS entity model ({@code XXXDbModel}) is accompanied by a sort test ({@code
 * XXXSortIT}) in {@code io.camunda.it.rdbms.db}.
 *
 * <p>Sort-field coverage is the most commonly forgotten test concern when adding a new entity.
 * DbModels that are sub-entities, variant models, or represent entities without user-facing sort
 * fields are listed in {@link #NO_SORT_IT_REQUIRED} and exempted from the check.
 *
 * <p>See {@link RdbmsDbModelCoverageArchTest} for an explanation of the dual-scan pattern.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.write.domain",
    importOptions = DoNotIncludeTestsOrTestJars.class)
class RdbmsDbModelSortITCoverageArchTest {

  // DbModels that do not require a corresponding SortIT. Two categories:
  //
  // 1. Sub-entities / variant models with no independent IT (same rationale as
  //    RdbmsDbModelCoverageArchTest.NON_INDEPENDENT_DB_MODELS).
  // 2. Domain entities that have an IT but whose write-path has no user-facing sort fields.
  static final Set<String> NO_SORT_IT_REQUIRED =
      Set.of(
          // Sub-entities / variant models — no standalone IT, so no SortIT either
          "UsageMetricTUDbModel", // TU variant; covered by UsageMetricIT
          "UserTaskMigrationDbModel", // migration helper; covered by UserTaskIT
          "BatchOperationItemDbModel", // sub-entity; covered by BatchOperationIT
          "GroupMemberDbModel", // member sub-entity; no GroupMemberSortIT
          "TenantMemberDbModel", // member sub-entity; no TenantMemberSortIT
          "RoleMemberDbModel", // member sub-entity; no RoleMemberSortIT
          "ProcessDefinitionVariableNameLookupDbModel", // variable-name cache; has an IT but no
          // sort fields
          "BatchOperationErrorDbModel", // error sub-entity of BatchOperation; no sort fields
          "HistoryDeletionTypeDbModel", // type sub-model for HistoryDeletion; no sort fields
          "EventTypeDbModel", // audit-event type enum model; no sort fields
          "UsageMetricStatisticsDbModel", // statistics sub-model; no sort fields
          "UsageMetricTUStatisticsDbModel", // TU statistics variant; no sort fields
          "UsageMetricTenantStatisticsDbModel", // tenant statistics variant; no sort fields
          "UsageMetricTUTenantStatisticsDbModel", // TU tenant statistics variant; no sort fields
          "JobMetricsBatchDbModel", // batch metrics; no sort fields
          // Domain entities without user-facing sort fields
          "CorrelatedMessageSubscriptionDbModel", // composite-key lookup, not a browseable list
          "HistoryDeletionDbModel", // transient internal records
          "SequenceFlowDbModel", // graph element, no ordering needed
          "UsageMetricDbModel" // statistical aggregation
          );

  // SortIT class simple names scanned from test-jars once at class-load time.
  static final Set<String> SORT_IT_SIMPLE_NAMES =
      StreamSupport.stream(
              new ClassFileImporter()
                  .withImportOption(new IncludeTestClassesAndTestJars())
                  .importPackages("io.camunda.it.rdbms.db")
                  .spliterator(),
              false)
          .filter(c -> c.getSimpleName().endsWith("SortIT"))
          .map(JavaClass::getSimpleName)
          .collect(Collectors.toUnmodifiableSet());

  @ArchTest
  static final ArchRule EACH_DB_MODEL_HAS_SORT_IT =
      ArchRuleDefinition.classes()
          .that()
          .haveSimpleNameEndingWith("DbModel")
          .should(haveCorrespondingSortItTest())
          .because(
              "each RDBMS entity model must be accompanied by a SortIT in io.camunda.it.rdbms.db.*"
                  + " to ensure sort-field coverage is tested against all databases");

  private static ArchCondition<JavaClass> haveCorrespondingSortItTest() {
    return new ArchCondition<>("have a corresponding SortIT in io.camunda.it.rdbms.db.*") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        if (javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
            || javaClass.isInterface()
            || NO_SORT_IT_REQUIRED.contains(javaClass.getSimpleName())) {
          events.add(satisfied(javaClass, "skipped — not a sortable independent entity"));
          return;
        }
        final String entityName = javaClass.getSimpleName().replace("DbModel", "");
        final String expectedSortIt = entityName + "SortIT";
        if (SORT_IT_SIMPLE_NAMES.contains(expectedSortIt)) {
          events.add(satisfied(javaClass, "found " + expectedSortIt));
        } else {
          events.add(
              violated(
                  javaClass,
                  javaClass.getSimpleName()
                      + " has no '"
                      + expectedSortIt
                      + "' in io.camunda.it.rdbms.db.*; add the missing SortIT or add the DbModel"
                      + " to NO_SORT_IT_REQUIRED if sorting is not applicable for this entity"));
        }
      }
    };
  }
}
