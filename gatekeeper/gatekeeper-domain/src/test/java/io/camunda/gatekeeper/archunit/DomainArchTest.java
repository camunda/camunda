/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests that enforce the architectural constraints of the gatekeeper-domain module.
 *
 * <p>The domain module is the pure core of the gatekeeper library. It must have zero framework
 * dependencies and follow hexagonal architecture conventions.
 */
final class DomainArchTest {

  private static JavaClasses domainClasses;

  @BeforeAll
  static void setUp() {
    domainClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.camunda.gatekeeper");
  }

  // --- Framework independence ---

  @Test
  void domainMustNotDependOnSpring() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.gatekeeper..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .because(
            "the domain module must be framework-independent — "
                + "Spring dependencies belong in gatekeeper-spring or gatekeeper-spring-boot-starter")
        .check(domainClasses);
  }

  @Test
  void domainMustNotDependOnJakartaServlet() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.gatekeeper..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.servlet..")
        .because("the domain module must not depend on servlet APIs")
        .check(domainClasses);
  }

  @Test
  void domainMustNotDependOnJacksonExceptAnnotations() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.gatekeeper..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("com.fasterxml.jackson.core..", "com.fasterxml.jackson.databind..")
        .because(
            "the domain module must not depend on Jackson runtime — "
                + "only pure metadata annotations (jackson-annotations) are allowed")
        .check(domainClasses);
  }

  // --- Hexagonal architecture: layering ---

  @Test
  void modelsMustNotDependOnSPIs() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.gatekeeper.model..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.camunda.gatekeeper.spi..")
        .because("models are pure data — they must not depend on SPIs")
        .check(domainClasses);
  }

  // --- Model constraints: records and enums only ---

  @Test
  void modelClassesMustBeRecordsOrEnumsOrSealedInterfaces() {
    classes()
        .that()
        .resideInAPackage("io.camunda.gatekeeper.model..")
        .and()
        .areTopLevelClasses()
        .should()
        .beRecords()
        .orShould()
        .beEnums()
        .orShould()
        .beInterfaces()
        .because(
            "domain model types must be records (immutable value objects), enums, "
                + "or sealed interfaces (composable type hierarchies)")
        .check(domainClasses);
  }

  // --- SPI constraints: interfaces only ---

  @Test
  void spisMustBeInterfaces() {
    classes()
        .that()
        .resideInAPackage("io.camunda.gatekeeper.spi..")
        .should()
        .beInterfaces()
        .because("SPI types define extension points — they must be interfaces")
        .check(domainClasses);
  }

  // --- Exception constraints ---

  @Test
  void exceptionsMustExtendRuntimeException() {
    classes()
        .that()
        .resideInAPackage("io.camunda.gatekeeper.exception..")
        .should()
        .beAssignableTo(RuntimeException.class)
        .because("domain exceptions must be unchecked (extend RuntimeException)")
        .check(domainClasses);
  }
}
