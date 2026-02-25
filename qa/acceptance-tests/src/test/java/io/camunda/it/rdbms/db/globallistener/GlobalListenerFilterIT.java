/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.globallistener;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.GlobalListenerFixtures.createAndSaveRandomGlobalListener;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.sort.GlobalListenerSort;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class GlobalListenerFilterIT {
  @TestTemplate
  public void shouldFindGlobalListenerWithEqListenerIdFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only one with "expectedEq" listenerId
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    final var expectedListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.listenerId("expectedEq"));
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));

    // when searching by equal listenerId
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .listenerIdOperations(Operation.eq("expectedEq"))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listener is found
    assertExpectedResult(searchResult, expectedListener);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithLikeListenerIdFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only two with listenerId matching "expectedLike*"
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.listenerId("expectedLike1"));
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.listenerId("expectedLike2"));

    // when searching by listenerId matching pattern
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .listenerIdOperations(Operation.like("expectedLike*"))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithInListenerIdFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only two with listenerId either "expectedIn1" or "expectedIn2"
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.listenerId("expectedIn1"));
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    createAndSaveRandomGlobalListener(
        testApplication, b -> b.listenerId("different" + nextStringId()));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.listenerId("expectedIn2"));

    // when searching by listenerId in provided set
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .listenerIdOperations(Operation.in("expectedIn1", "expectedIn2"))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithEqTypeFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only one with "expectedEq" type
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    final var expectedListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("expectedEq"));
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));

    // when searching by equal type
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .typeOperations(Operation.eq("expectedEq"))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listener is found
    assertExpectedResult(searchResult, expectedListener);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithLikeTypeFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only two with type matching "expectedLike*"
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("expectedLike1"));
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("expectedLike2"));

    // when searching by type matching pattern
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .typeOperations(Operation.like("expectedLike*"))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithInTypeFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only two with type either "expectedIn1" or "expectedIn2"
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("expectedIn1"));
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    createAndSaveRandomGlobalListener(testApplication, b -> b.type("different" + nextStringId()));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("expectedIn2"));

    // when searching by type in provided set
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .typeOperations(Operation.in("expectedIn1", "expectedIn2"))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithEqRetriesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only one with retries=2
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    final var expectedListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(2).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(4).type(testIdentifier));

    // when searching by equal retries
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .retriesOperations(Operation.eq(2))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listener is found
    assertExpectedResult(searchResult, expectedListener);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithGtRetriesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with retries > 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(5).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(4).type(testIdentifier));

    // when searching by greater retries
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .retriesOperations(Operation.gt(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithGteRetriesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with retries >= 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(2).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(4).type(testIdentifier));

    // when searching by greater or equal retries
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .retriesOperations(Operation.gte(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithLtRetriesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with retries < 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(5).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(4).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(2).type(testIdentifier));

    // when searching by smaller retries
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .retriesOperations(Operation.lt(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithLteRetriesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with retries <= 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(5).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(6).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(4).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(2).type(testIdentifier));

    // when searching by smaller or equal retries
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .retriesOperations(Operation.lte(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithInRetriesFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with retries either 2 or 5
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(1).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(2).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.retries(4).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.retries(5).type(testIdentifier));

    // when searching by retries in provided set
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .retriesOperations(Operation.in(2, 5))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithAfterNonGlobalFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // given multiple listeners but only one with afterNonGlobal=true
    createAndSaveRandomGlobalListener(testApplication, b -> b.afterNonGlobal(false));
    final var expectedListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.afterNonGlobal(true));
    createAndSaveRandomGlobalListener(testApplication, b -> b.afterNonGlobal(false));
    createAndSaveRandomGlobalListener(testApplication, b -> b.afterNonGlobal(false));

    // when searching by true afterNonGlobal
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder().afterNonGlobal(true).build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listener is found
    assertExpectedResult(searchResult, expectedListener);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithEqPriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only one with priority=2
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    final var expectedListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(2).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(4).type(testIdentifier));

    // when searching by equal priority
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .priorityOperations(Operation.eq(2))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listener is found
    assertExpectedResult(searchResult, expectedListener);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithGtPriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with priority > 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(5).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(4).type(testIdentifier));

    // when searching by greater priority
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .priorityOperations(Operation.gt(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithGtePriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with priority >= 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(2).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(4).type(testIdentifier));

    // when searching by greater or equal priority
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .priorityOperations(Operation.gte(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithLtPriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with priority < 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(5).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(4).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(2).type(testIdentifier));

    // when searching by smaller priority
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .priorityOperations(Operation.lt(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithLtePriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with priority <= 3
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(5).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(6).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(4).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(2).type(testIdentifier));

    // when searching by smaller or equal priority
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .priorityOperations(Operation.lte(3))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  @TestTemplate
  public void shouldFindGlobalListenerWithInPriorityFilter(
      final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple listeners but only two with priority either 2 or 5
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(1).type(testIdentifier));
    final var expectedListener1 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(2).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(3).type(testIdentifier));
    createAndSaveRandomGlobalListener(testApplication, b -> b.priority(4).type(testIdentifier));
    final var expectedListener2 =
        createAndSaveRandomGlobalListener(testApplication, b -> b.priority(5).type(testIdentifier));

    // when searching by priority in provided set
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder()
                        .types(testIdentifier)
                        .priorityOperations(Operation.in(2, 5))
                        .build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b)),
                ResourceAccessChecks.disabled());

    // then only the expected listeners are found
    assertExpectedResult(searchResult, expectedListener1, expectedListener2);
  }

  private void assertExpectedResult(
      final SearchQueryResult<GlobalListenerEntity> searchResult,
      final GlobalListenerDbModel... expected) {
    assertThat(searchResult.total()).isEqualTo(expected.length);
    assertThat(searchResult.items()).hasSize(expected.length);
    final var actualIds =
        searchResult.items().stream().map(GlobalListenerEntity::id).collect(Collectors.toSet());
    final var expectedIds =
        Arrays.stream(expected).map(GlobalListenerDbModel::id).collect(Collectors.toSet());
    assertThat(actualIds).isEqualTo(expectedIds);
  }
}
