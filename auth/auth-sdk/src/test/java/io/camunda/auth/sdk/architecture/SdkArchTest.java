/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.sdk.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests that enforce the public API constraints of the auth-sdk module.
 *
 * <p>The SDK is the consumer-facing module. It provides a clean facade and must not leak internal
 * implementation details.
 */
final class SdkArchTest {

  private static JavaClasses sdkClasses;

  @BeforeAll
  static void setUp() {
    sdkClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.camunda.auth.sdk");
  }

  @Test
  void sdkMustNotDependOnSpring() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.sdk..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..")
        .because(
            "the SDK facade must be framework-independent — "
                + "consumers should not need Spring to use it")
        .check(sdkClasses);
  }

  @Test
  void sdkMustNotDependOnSpringImplementations() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.sdk..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.camunda.auth.spring..", "io.camunda.auth.starter..")
        .because("the SDK must not depend on Spring integration or auto-configuration modules")
        .check(sdkClasses);
  }

  @Test
  void sdkMustNotDependOnPersistence() {
    noClasses()
        .that()
        .resideInAPackage("io.camunda.auth.sdk..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("io.camunda.auth.persist..")
        .because("the SDK must not depend on persistence adapter modules")
        .check(sdkClasses);
  }

  @Test
  void sdkFacadeMustBeFinal() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.sdk")
        .and()
        .areNotInterfaces()
        .should()
        .haveModifier(JavaModifier.FINAL)
        .because("SDK facade classes must be final to prevent subclassing")
        .check(sdkClasses);
  }

  @Test
  void sdkPublicContractsMustBeInterfaces() {
    classes()
        .that()
        .resideInAPackage("io.camunda.auth.sdk")
        .and()
        .areTopLevelClasses()
        .and()
        .haveSimpleNameEndingWith("Facade")
        .should()
        .beInterfaces()
        .because("SDK facade contracts (e.g. TokenExchangeFacade) must be interfaces")
        .check(sdkClasses);
  }
}
