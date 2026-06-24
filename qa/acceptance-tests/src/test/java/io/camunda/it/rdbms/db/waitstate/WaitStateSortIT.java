/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.waitstate;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.WaitStateFixtures.createAndSaveRandomWaitStates;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.WaitStateDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.filter.ElementInstanceWaitStateFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ElementInstanceWaitStateQuery;
import io.camunda.search.sort.WaitStateSort;
import io.camunda.search.sort.WaitStateSort.Builder;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class WaitStateSortIT {

  public static final long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByElementInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.elementInstanceKey().asc(),
        Comparator.comparing(WaitStateEntity::elementInstanceKey));
  }

  @TestTemplate
  public void shouldSortByElementInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.elementInstanceKey().desc(),
        Comparator.comparing(WaitStateEntity::elementInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(WaitStateEntity::processInstanceKey));
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().desc(),
        Comparator.comparing(WaitStateEntity::processInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByRootProcessInstanceKeyAsc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.rootProcessInstanceKey().asc(),
        Comparator.comparing(WaitStateEntity::rootProcessInstanceKey));
  }

  @TestTemplate
  public void shouldSortByRootProcessInstanceKeyDesc(
      final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.rootProcessInstanceKey().desc(),
        Comparator.comparing(WaitStateEntity::rootProcessInstanceKey).reversed());
  }

  @TestTemplate
  public void shouldSortByElementIdAsc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.elementId().asc(),
        Comparator.comparing(WaitStateEntity::elementId));
  }

  @TestTemplate
  public void shouldSortByElementIdDesc(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.elementId().desc(),
        Comparator.comparing(WaitStateEntity::elementId).reversed());
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<WaitStateSort>> sortBuilder,
      final Comparator<WaitStateEntity> comparator) {
    final RdbmsWriters rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    final WaitStateDbReader reader = rdbmsService.getWaitStateReader();

    final var processInstanceKey = nextKey();
    createAndSaveRandomWaitStates(
        rdbmsWriters,
        b -> b.processInstanceKey(processInstanceKey).rootProcessInstanceKey(nextKey()));

    final var searchResult =
        reader
            .search(
                new ElementInstanceWaitStateQuery(
                    new ElementInstanceWaitStateFilter.Builder()
                        .processInstanceKeys(processInstanceKey)
                        .build(),
                    WaitStateSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled())
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
