/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.globallistener;

import static io.camunda.it.rdbms.db.fixtures.GlobalListenerFixtures.createAndSaveRandomGlobalListeners;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.sort.GlobalListenerSort;
import io.camunda.search.sort.GlobalListenerSort.Builder;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class GlobalListenerSortIT {

  @BeforeAll
  public static void a() {
    final int i = 0;
  }

  @TestTemplate
  public void shouldSortByIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(testApplication, b -> b.id().asc(), Comparator.comparing(GlobalListenerEntity::id));
  }

  @TestTemplate
  public void shouldSortByIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.id().desc(),
        Comparator.comparing(GlobalListenerEntity::id).reversed());
  }

  @TestTemplate
  public void shouldSortByListenerIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.listenerId().asc(),
        Comparator.comparing(GlobalListenerEntity::listenerId));
  }

  @TestTemplate
  public void shouldSortByListenerIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.listenerId().desc(),
        Comparator.comparing(GlobalListenerEntity::listenerId).reversed());
  }

  @TestTemplate
  public void shouldSortByTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication, b -> b.type().asc(), Comparator.comparing(GlobalListenerEntity::type));
  }

  @TestTemplate
  public void shouldSortByTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.type().desc(),
        Comparator.comparing(GlobalListenerEntity::type).reversed());
  }

  @TestTemplate
  public void shouldSortByAfterNonGlobalAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.afterNonGlobal().asc(),
        Comparator.comparing(GlobalListenerEntity::afterNonGlobal));
  }

  @TestTemplate
  public void shouldSortByAfterNonGlobalDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.afterNonGlobal().desc(),
        Comparator.comparing(GlobalListenerEntity::afterNonGlobal).reversed());
  }

  @TestTemplate
  public void shouldSortByPriorityAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.priority().asc(),
        Comparator.comparing(GlobalListenerEntity::priority));
  }

  @TestTemplate
  public void shouldSortByPriorityDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.priority().desc(),
        Comparator.comparing(GlobalListenerEntity::priority).reversed());
  }

  @TestTemplate
  public void shouldSortBySourceAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication, b -> b.source().asc(), Comparator.comparing(GlobalListenerEntity::source));
  }

  @TestTemplate
  public void shouldSortBySourceDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.source().desc(),
        Comparator.comparing(GlobalListenerEntity::source).reversed());
  }

  @TestTemplate
  public void shouldSortByListenerTypeAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.listenerType().asc(),
        Comparator.comparing(GlobalListenerEntity::listenerType));
  }

  @TestTemplate
  public void shouldSortByListenerTypeDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication,
        b -> b.listenerType().desc(),
        Comparator.comparing(GlobalListenerEntity::listenerType).reversed());
  }

  private void testSorting(
      final CamundaRdbmsTestApplication testApplication,
      final Function<Builder, ObjectBuilder<GlobalListenerSort>> sortBuilder,
      final Comparator<GlobalListenerEntity> comparator) {
    final var listenersInThisTest = createAndSaveRandomGlobalListeners(testApplication, 20, b -> b);

    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        // only consider entities specifically created for this test
                        .listenerIdOperations(
                            Operation.in(
                                listenersInThisTest.stream()
                                    .map(GlobalListenerDbModel::listenerId)
                                    .toList()))
                        .build(),
                    GlobalListenerSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled())
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
