/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.satisfied;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.db.rdbms.write.service.ProcessInstanceDependant;
import io.camunda.db.rdbms.write.service.RdbmsWriter;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Architectural rules for the RDBMS data layer.
 *
 * <p>Every RDBMS writer that stores process-instance-scoped data (i.e. depends on a {@code
 * *DbModel} with a {@code processInstanceKey} field) must extend {@link ProcessInstanceDependant}
 * so that {@code HistoryCleanupService} can delete its rows when a process instance is archived.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.write",
    importOptions = DoNotIncludeTestsOrTestJars.class)
class RdbmsProcessInstanceDependantArchTest {

  /**
   * DbModel simple names (pre-scanned from {@code io.camunda.db.rdbms.write.domain}) whose {@code
   * processInstanceKey} member is a foreign-key reference to a process instance.
   *
   * <p>{@code ProcessInstanceDbModel} is intentionally excluded: its {@code processInstanceKey} is
   * the entity's own primary key, not a FK. {@code ProcessInstanceWriter} manages the root entity
   * and uses its own specialised deletion path.
   */
  static final Set<String> PROCESS_INSTANCE_DEPENDENT_DB_MODELS =
      StreamSupport.stream(
              new ClassFileImporter()
                  .withImportOption(new DoNotIncludeTestsOrTestJars())
                  .importPackages("io.camunda.db.rdbms.write.domain")
                  .spliterator(),
              false)
          .filter(c -> c.getSimpleName().endsWith("DbModel"))
          .filter(c -> !c.getSimpleName().equals("ProcessInstanceDbModel"))
          .filter(
              c ->
                  c.getFields().stream().anyMatch(f -> f.getName().equals("processInstanceKey"))
                      || c.getMethods().stream()
                          .anyMatch(m -> m.getName().equals("processInstanceKey")))
          .map(JavaClass::getSimpleName)
          .collect(Collectors.toUnmodifiableSet());

  /**
   * Writer simple names whose process-instance-keyed data is intentionally cleaned up by a
   * mechanism other than {@link ProcessInstanceDependant#deleteProcessInstanceRelatedData}.
   *
   * <p>{@code BatchOperationWriter}: {@code BatchOperationItemDbModel} carries a {@code
   * processInstanceKey} for informational context, but batch-operation items are retained for
   * operational visibility and cleaned up by a TTL-based path ({@code
   * BatchOperationWriter.cleanupHistory}). Hooking them into the process-instance cleanup path
   * would delete batch-op history prematurely when the referenced process instance is archived.
   */
  static final Set<String> WRITERS_WITH_SEPARATE_CLEANUP_PATH = Set.of("BatchOperationWriter");

  @ArchTest
  static final ArchRule RDBMS_WRITERS_DEPENDING_ON_PROCESS_INSTANCE_DATA_MUST_EXTEND_DEPENDANT =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.db.rdbms.write.service")
          .and()
          .implement(RdbmsWriter.class)
          .should(mustExtendProcessInstanceDependantIfRequired())
          .because(
              "Any RDBMS writer storing process-instance-scoped data must extend"
                  + " ProcessInstanceDependant so HistoryCleanupService can delete its rows"
                  + " when a process instance is archived."
                  + " Fix: extend ProcessInstanceDependant, make the SQL mapper extend"
                  + " ProcessInstanceDependantMapper, add the two delete SQL statements,"
                  + " and register the writer in"
                  + " HistoryCleanupService.rootProcessInstanceDependentChildWriters."
                  + " If cleanup is handled by a different mechanism, add the writer to"
                  + " RdbmsProcessInstanceDependantArchTest.WRITERS_WITH_SEPARATE_CLEANUP_PATH with an explanation.");

  private static ArchCondition<JavaClass> mustExtendProcessInstanceDependantIfRequired() {
    return new ArchCondition<>(
        "extend ProcessInstanceDependant if it depends on a process-instance DbModel") {
      @Override
      public void check(final JavaClass writerClass, final ConditionEvents events) {
        final Set<String> offendingModels =
            writerClass.getDirectDependenciesFromSelf().stream()
                .map(dep -> dep.getTargetClass().getSimpleName())
                .filter(PROCESS_INSTANCE_DEPENDENT_DB_MODELS::contains)
                .collect(Collectors.toSet());

        if (offendingModels.isEmpty()) {
          events.add(satisfied(writerClass, "no process-instance DbModel dependency"));
          return;
        }
        if (WRITERS_WITH_SEPARATE_CLEANUP_PATH.contains(writerClass.getSimpleName())) {
          events.add(
              satisfied(
                  writerClass,
                  writerClass.getSimpleName()
                      + " is whitelisted — cleanup handled outside ProcessInstanceDependant"));
          return;
        }
        if (writerClass.isAssignableTo(ProcessInstanceDependant.class)) {
          events.add(
              satisfied(
                  writerClass,
                  writerClass.getSimpleName()
                      + " correctly extends ProcessInstanceDependant (models: "
                      + offendingModels
                      + ")"));
        } else {
          events.add(
              violated(
                  writerClass,
                  writerClass.getSimpleName()
                      + " depends on process-instance DbModel(s) "
                      + offendingModels
                      + " but does not extend ProcessInstanceDependant."
                      + " Its rows will NOT be cleaned up during history cleanup."));
        }
      }
    };
  }
}
