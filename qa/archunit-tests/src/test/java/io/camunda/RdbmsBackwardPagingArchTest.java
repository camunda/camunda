/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enforces the reader half of the backward keyset pagination contract.
 *
 * <p>A {@code before} page is answered by seeking against the display direction, so the mapper
 * hands the reader its rows reversed and the reader has to turn them back. {@code
 * AbstractEntityReader#executePagedQuery} does that on its own; a reader that assembles its result
 * some other way — the statistics readers post-process their rows — has to call {@code
 * restoreDisplayOrder} itself. Forgetting it is silent: the page comes back in the wrong order, or
 * from the wrong end of the range entirely.
 *
 * <p>The mapper half — that the ORDER BY flips at all — is covered by {@code
 * KeySetStatementOrderByContractTest} and {@code KeySetPaginationOrderByTest} in {@code db/rdbms}.
 */
@AnalyzeClasses(
    packages = "io.camunda.db.rdbms.read",
    importOptions = DoNotIncludeTestsOrTestJars.class)
public final class RdbmsBackwardPagingArchTest {

  private static final String READER_PACKAGE = "io.camunda.db.rdbms.read.service";

  /**
   * Builds a page but never reads it back: {@code SequenceFlowMapper.search} returns every sequence
   * flow of one process instance and renders no keyset filter, ORDER BY or LIMIT at all, so there
   * is no seek order to restore. Paginating that statement means removing this exemption.
   */
  private static final String UNPAGINATED_READER = READER_PACKAGE + ".SequenceFlowDbReader";

  @ArchTest
  static final ArchRule PAGED_READERS_MUST_RESTORE_THE_DISPLAY_ORDER =
      ArchRuleDefinition.classes()
          .that()
          .resideInAPackage(READER_PACKAGE)
          .should(restoreTheDisplayOrderOfABackwardPage())
          .because(
              "a reader that turns a SearchQueryPage into a DbQueryPage may be handed a `before`"
                  + " cursor, and the rows for such a page come back from the database in reverse"
                  + " display order (see Commons.orderBy). Route the query through"
                  + " executePagedQuery, or call restoreDisplayOrder on the rows yourself.");

  private static ArchCondition<JavaClass> restoreTheDisplayOrderOfABackwardPage() {
    return new ArchCondition<>("restore the display order of a backward page") {
      @Override
      public void check(final JavaClass reader, final ConditionEvents events) {
        if (reader.getFullName().equals(UNPAGINATED_READER)) {
          return;
        }

        final Set<String> calledReaderMethods =
            reader.getMethodCallsFromSelf().stream()
                // AbstractEntityReader is package-private, so javac gives every public reader a
                // bridge method for each public method it inherits. Those bridges delegate to
                // convertPaging without the reader ever paging anything, and would otherwise make
                // this rule fire on readers that do not paginate at all.
                .filter(call -> !call.getOrigin().getModifiers().contains(JavaModifier.BRIDGE))
                .filter(call -> call.getTargetOwner().getPackageName().startsWith(READER_PACKAGE))
                .map(JavaCall::getName)
                .collect(Collectors.toSet());

        if (!calledReaderMethods.contains("convertPaging")
            || calledReaderMethods.contains("executePagedQuery")
            || calledReaderMethods.contains("restoreDisplayOrder")) {
          return;
        }

        events.add(
            violated(
                reader,
                reader.getSimpleName()
                    + " builds a DbQueryPage with convertPaging but never restores the display"
                    + " order of a backward page: it calls neither executePagedQuery nor"
                    + " restoreDisplayOrder."));
      }
    };
  }
}
