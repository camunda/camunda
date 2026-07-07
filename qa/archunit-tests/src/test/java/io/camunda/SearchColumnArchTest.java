/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.db.rdbms.sql.columns.SearchColumn;

/**
 * Enforces that all {@link SearchColumn} implementations are Java enums.
 *
 * <p>Each {@code *DbReader} constructor passes {@code SomeSearchColumn.values()} to {@link
 * io.camunda.db.rdbms.read.service.AbstractEntityReader}, which builds a lookup map over the array.
 * Only enums have a compiler-generated {@code values()} method, so a class-based implementation
 * would fail to compile at the call sites in the {@code *DbReader} constructors.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.sql.columns",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class SearchColumnArchTest {

  @ArchTest
  static final ArchRule SEARCH_COLUMNS_MUST_BE_ENUMS =
      ArchRuleDefinition.classes()
          .that()
          .implement(SearchColumn.class)
          .and()
          .areNotInterfaces()
          .should(
              new ArchCondition<>("be a Java enum") {
                @Override
                public void check(final JavaClass item, final ConditionEvents events) {
                  if (!item.isEnum()) {
                    events.add(
                        violated(
                            item,
                            item.getName()
                                + " implements SearchColumn but is not an enum."
                                + " SearchColumn implementations must be enums so that"
                                + " *DbReader constructors can call .values() on them."));
                  }
                }
              })
          .because(
              "*DbReader constructors call SomeSearchColumn.values() and pass the array to"
                  + " AbstractEntityReader; only enums have a compiler-generated values() method,"
                  + " so a class-based implementation would fail to compile at those call sites");
}
