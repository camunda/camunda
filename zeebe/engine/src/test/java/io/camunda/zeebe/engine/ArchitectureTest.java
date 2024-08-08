/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
}
