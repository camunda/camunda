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
 * <p>{@link io.camunda.db.rdbms.read.service.AbstractEntityReader} calls {@code .values()} on the
 * {@code SearchColumn} type to enumerate sortable fields. A non-enum implementation breaks this at
 * runtime because only enums have a generated {@code values()} method.
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
                                + " AbstractEntityReader can call .values() on them."));
                  }
                }
              })
          .because(
              "AbstractEntityReader relies on SearchColumn.values() to enumerate sortable"
                  + " fields; a class-based implementation breaks this at runtime");
}
