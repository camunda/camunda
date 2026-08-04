/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;

/**
 * Enforces that every RDBMS write-side data carrier ({@code *DbModel}) is a record, not a plain
 * mutable class.
 *
 * <p>Records give these write-side data carriers structural, constructor-validated
 * equality/hashCode/toString and rule out the class-level mutability (arbitrary setters,
 * uncontrolled field reassignment) that a plain class allows, keeping them consistent with the rest
 * of the {@code write.domain} package. This does not guarantee deep immutability of every component
 * -- a record may still hold a mutable collection (e.g. {@code BatchOperationDbModel.errors},
 * populated by MyBatis via {@code <collection>} after construction); that is an accepted,
 * intentional exception to per-component immutability, not something this rule polices. Enum
 * sub-models (e.g. {@code HistoryDeletionDbModel.HistoryDeletionTypeDbModel}) are excluded
 * structurally since a Java enum can never be a record.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.write.domain",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class RdbmsDbModelMustBeRecordArchTest {

  @ArchTest
  static final ArchRule DB_MODELS_MUST_BE_RECORDS =
      ArchRuleDefinition.classes()
          .that()
          .haveSimpleNameEndingWith("DbModel")
          .and()
          .areNotInterfaces()
          .and()
          .areNotEnums()
          .should()
          .beRecords()
          .because(
              "DbModels must be records, not plain mutable classes, to rule out uncontrolled"
                  + " field mutation and stay consistent with the rest of the write.domain"
                  + " package");
}
