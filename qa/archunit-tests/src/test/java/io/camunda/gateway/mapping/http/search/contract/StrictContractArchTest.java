/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * ArchUnit rules for the strict contract response layer.
 *
 * <p>These rules enforce that:
 *
 * <ol>
 *   <li>All generated strict contract DTOs are Java {@code record}s (immutability).
 *   <li>All generated strict contract DTOs follow the {@code Generated*StrictContract} naming
 *       convention (provenance — the {@code @Generated} source annotation has SOURCE retention and
 *       is not available for runtime/bytecode checks).
 *   <li>Generated strict contract DTOs do not depend on protocol-model search entity types ({@code
 *       io.camunda.search.entities}), ensuring clean separation between the internal domain model
 *       and the external API contract.
 * </ol>
 */
@AnalyzeClasses(
    packages = "io.camunda.gateway.mapping.http.search.contract.generated",
    importOptions = ImportOption.DoNotIncludeTests.class)
public final class StrictContractArchTest {

  /**
   * Every concrete type in the generated strict contract package must be a Java {@code record}.
   * Enums and interfaces (e.g. sealed-interface hierarchies for union types) are excluded.
   */
  @ArchTest
  static final ArchRule GENERATED_STRICT_CONTRACTS_MUST_BE_RECORDS =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.gateway.mapping.http.search.contract.generated")
          .and()
          .areTopLevelClasses()
          .and()
          .areNotEnums()
          .and()
          .areNotInterfaces()
          .should(
              new ArchCondition<>("be a Java record") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  if (!item.isRecord()) {
                    events.add(
                        violated(
                            item,
                            String.format(
                                "Class '%s' in the generated strict contract package is not a "
                                    + "Java record — generated DTOs must be immutable records",
                                item.getName())));
                  }
                }
              })
          .because(
              "generated strict contract DTOs must be immutable records "
                  + "to guarantee null-safety via the builder pattern");

  /**
   * Every generated strict contract DTO must follow the {@code Generated*StrictContract} naming
   * convention. The {@code @jakarta.annotation.Generated} annotation has {@code SOURCE} retention
   * and is not preserved in bytecode, so we enforce the naming pattern instead.
   */
  @ArchTest
  static final ArchRule GENERATED_STRICT_CONTRACTS_MUST_FOLLOW_NAMING_CONVENTION =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage("io.camunda.gateway.mapping.http.search.contract.generated")
          .and()
          .areTopLevelClasses()
          .and()
          .areNotEnums()
          .and()
          .areNotInterfaces()
          .should()
          .haveSimpleNameStartingWith("Generated")
          .andShould()
          .haveSimpleNameEndingWith("StrictContract")
          .because(
              "generated strict contract DTOs must follow the Generated*StrictContract naming "
                  + "convention so that tooling can identify code-generated types");

  /**
   * Generated strict contract DTOs must not depend on protocol-model search entity types. The
   * adapter layer ({@code io.camunda.gateway.mapping.http.search.contract}) bridges between
   * protocol-model entities and the generated strict contract; the generated types themselves must
   * remain decoupled.
   */
  @ArchTest
  static final ArchRule GENERATED_STRICT_CONTRACTS_MUST_NOT_DEPEND_ON_SEARCH_ENTITIES =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("io.camunda.gateway.mapping.http.search.contract.generated")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("io.camunda.search.entities..")
          .because(
              "generated strict contract DTOs must not reference protocol-model search entities — "
                  + "use the adapter layer to bridge between internal entities and the API contract");
}
