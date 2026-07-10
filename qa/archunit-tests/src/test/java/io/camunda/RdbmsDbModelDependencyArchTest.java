/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;

/**
 * Enforces that RDBMS DbModel classes do not depend on search entity records.
 *
 * <p>DbModels are write-side data carriers — they map directly to database rows and are consumed by
 * MyBatis mappers. Search entity records (classes in {@code io.camunda.search.entities} whose
 * simple name ends with {@code Entity}) are read-side types produced by the exporter and consumed
 * by search queries. Mixing the two creates a coupling between the write and read models that makes
 * it harder to evolve each independently.
 *
 * <p>Nested types inside entity records (e.g. state enums like {@code
 * BatchOperationEntity.BatchOperationItemState}) are referenced by their own bytecode name (e.g.
 * {@code BatchOperationEntity$BatchOperationItemState}) and do not end with {@code Entity}, so they
 * are permitted.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.write.domain",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class RdbmsDbModelDependencyArchTest {

  private static final DescribedPredicate<JavaClass> IS_SEARCH_ENTITY_RECORD =
      Predicates.resideInAPackage("io.camunda.search.entities..")
          .and(
              new DescribedPredicate<>("have simple name ending with 'Entity'") {
                @Override
                public boolean test(final JavaClass javaClass) {
                  return javaClass.getSimpleName().endsWith("Entity");
                }
              });

  @ArchTest
  static final ArchRule DB_MODELS_MUST_NOT_DEPEND_ON_SEARCH_ENTITIES =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("io.camunda.db.rdbms.write.domain..")
          .should()
          .dependOnClassesThat(IS_SEARCH_ENTITY_RECORD)
          .because(
              "DbModels are write-side data carriers and must not depend on search entity"
                  + " records (read-side types). Use only the nested enum types"
                  + " (e.g. SomeEntity$SomeState) if shared constants are needed, or extract"
                  + " shared enums to a dedicated domain-enums package.");
}
