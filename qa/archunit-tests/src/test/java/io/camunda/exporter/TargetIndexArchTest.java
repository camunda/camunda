/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass.Predicates;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.store.ExporterBatchWriter;

@AnalyzeClasses(packages = "io.camunda.exporter", importOptions = DoNotIncludeTestsOrTestJars.class)
public class TargetIndexArchTest {
  @ArchTest
  static final ArchRule TARGET_INDEXES_SHOULD_ONLY_BE_CREATED_BY_BATCH_WRITER =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAPackage("io.camunda.exporter.index..")
          .and()
          .haveNameNotMatching("^(name).*")
          .should()
          .onlyBeCalled()
          .byClassesThat()
          .areAssignableTo(
              DescribedPredicate.or(
                  Predicates.assignableTo(TargetIndex.class),
                  Predicates.assignableTo(ExporterBatchWriter.class)))
          .because(
              "TargetIndex instances should only be created by the ExporterBatchWriter to ensure that all writes are sent to the correct index.");
}
