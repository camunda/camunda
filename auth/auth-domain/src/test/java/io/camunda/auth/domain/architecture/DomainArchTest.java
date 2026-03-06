/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests that enforce the architectural constraints of the auth-domain module.
 *
 * <p>The domain module is the pure core of the auth library. It must have zero framework
 * dependencies and follow hexagonal architecture conventions.
 */
final class DomainArchTest {

  private static JavaClasses domainClasses;

  @BeforeAll
  static void setUp() {
    domainClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.camunda.auth.domain");
  }

  // --- Framework independence ---

  @Test
  void domainMustNotDependOnSpring() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .because(
            "the domain module must be framework-independent — "
                + "Spring dependencies belong in auth-spring or auth-spring-boot-starter")
        .check(domainClasses);
  }

  @Test
  void domainMustNotDependOnJakartaServlet() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.domain..")
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
        .resideInAPackage("io.camunda.auth.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "com.fasterxml.jackson.core..", "com.fasterxml.jackson.databind..", "tools.jackson..")
        .because(
            "the domain module must not depend on Jackson runtime — "
                + "only pure metadata annotations (jackson-annotations) are allowed")
        .check(domainClasses);
  }

  // --- Hexagonal architecture: layering ---

  @Test
  void portsMustNotDependOnServices() {
    noClasses()
        .that()
        .resideInAnyPackage("io.camunda.auth.domain.port..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.camunda.auth.domain.service..")
        .because("ports (interfaces) must not depend on service implementations")
        .check(domainClasses);
  }

  @Test
  void modelsMustNotDependOnPortsOrServices() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.domain.model..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "io.camunda.auth.domain.port..",
            "io.camunda.auth.domain.service..",
            "io.camunda.auth.domain.spi..",
            "io.camunda.auth.domain.store..")
        .because("models are pure data — they must not depend on ports, services, or SPIs")
        .check(domainClasses);
  }

  @Test
  void spisMustNotDependOnServices() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.domain.spi..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.camunda.auth.domain.service..")
        .because("SPIs (extension points) must not depend on service implementations")
        .check(domainClasses);
  }

  // --- Model constraints: records and enums only ---

  @Test
  void modelClassesMustBeRecordsOrEnums() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.domain.model..")
        .and()
        .areTopLevelClasses()
        .should()
        .beRecords()
        .orShould()
        .beEnums()
        .because("domain model types must be records (immutable value objects) or enums")
        .check(domainClasses);
  }

  @Test
  void modelBuildersMustBeFinal() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.domain.model..")
        .and()
        .haveSimpleNameEndingWith("Builder")
        .should()
        .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
        .because("model builders must be final to prevent subclassing")
        .check(domainClasses);
  }

  // --- Port and SPI constraints: interfaces only ---

  @Test
  void inboundPortsMustBeInterfaces() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.domain.port.inbound..")
        .should()
        .beInterfaces()
        .because("inbound ports define contracts — they must be interfaces")
        .check(domainClasses);
  }

  @Test
  void outboundPortsMustBeInterfaces() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.domain.port.outbound..")
        .should()
        .beInterfaces()
        .because("outbound ports define contracts — they must be interfaces")
        .check(domainClasses);
  }

  @Test
  void spisMustBeInterfaces() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.domain.spi..")
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
        .resideInAPackage("io.camunda.auth.domain.exception..")
        .should()
        .beAssignableTo(RuntimeException.class)
        .because("domain exceptions must be unchecked (extend RuntimeException)")
        .check(domainClasses);
  }
}
