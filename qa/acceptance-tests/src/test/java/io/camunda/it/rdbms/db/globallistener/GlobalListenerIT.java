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
import static io.camunda.it.rdbms.db.fixtures.GlobalListenerFixtures.createAndSaveRandomGlobalListeners;
import static io.camunda.it.rdbms.db.fixtures.GlobalListenerFixtures.createRandomGlobalListener;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.GlobalListenerDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.filter.GlobalListenerFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.sort.GlobalListenerSort;
import io.camunda.security.reader.ResourceAccessChecks;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class GlobalListenerIT {

  @TestTemplate
  public void shouldCreateAndGetGlobalListenerByListenerIdAndListenerType(
      final CamundaRdbmsTestApplication testApplication) {
    // given a randomized listener
    final GlobalListenerDbModel globalListener = createRandomGlobalListener(b -> b);

    // when creating it and retrieving it using the global listener writer and reader
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    rdbmsWriters.getGlobalListenerWriter().create(globalListener);
    rdbmsWriters.flush();
    final var entity =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .getGlobalListener(
                globalListener.listenerId(),
                globalListener.listenerType(),
                ResourceAccessChecks.disabled());

    // then the listener is correctly retrieved
    assertThat(entity).isNotNull();
    assertDbModelEqualToEntity(globalListener, entity);
  }

  @TestTemplate
  public void shouldFindAllGlobalListenersPaged(final CamundaRdbmsTestApplication testApplication) {
    // This value is set in the "type" of all listeners and used in the search query in order to
    // ensure that only the listeners created in this specific test are returned
    final String testIdentifier = nextStringId();

    // given multiple global listeners
    createAndSaveRandomGlobalListeners(testApplication, 20, b -> b.type(testIdentifier));

    // when searching with no filter and paging
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder().types(testIdentifier).build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))),
                ResourceAccessChecks.disabled());

    // then all listeners are found and the paged ones are returned
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(20);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldFindAllGlobalListenersPagedWithHasMoreHits(
      final CamundaRdbmsTestApplication testApplication) {
    final String testIdentifier = nextStringId();

    // given more than 100 global listeners with the same type
    createAndSaveRandomGlobalListeners(testApplication, 120, b -> b.type(testIdentifier));

    // when searching with paging
    final var searchResult =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .search(
                new GlobalListenerQuery(
                    new GlobalListenerFilter.Builder().types(testIdentifier).build(),
                    GlobalListenerSort.of(b -> b),
                    SearchQueryPage.of(b -> b.from(0).size(5))),
                ResourceAccessChecks.disabled());

    // then hasMoreTotalItems is true
    assertThat(searchResult).isNotNull();
    assertThat(searchResult.total()).isEqualTo(100);
    assertThat(searchResult.hasMoreTotalItems()).isEqualTo(true);
    assertThat(searchResult.items()).hasSize(5);
  }

  @TestTemplate
  public void shouldUpdateGlobalListener(final CamundaRdbmsTestApplication testApplication) {
    // given an existing global listener
    final GlobalListenerDbModel globalListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("old-job-type"));

    // when it is updated with a new version with the same listener id and listener type
    final GlobalListenerDbModel updatedGlobalListener =
        createRandomGlobalListener(
            b ->
                b.listenerId(globalListener.listenerId())
                    .listenerType(globalListener.listenerType())
                    .type("new-job-type"));
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    rdbmsWriters.getGlobalListenerWriter().update(updatedGlobalListener);
    rdbmsWriters.flush();

    // then the update version can be retrieved
    final var instance =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .getGlobalListener(
                globalListener.listenerId(),
                globalListener.listenerType(),
                ResourceAccessChecks.disabled());
    assertThat(instance).isNotNull();
    assertDbModelEqualToEntity(updatedGlobalListener, instance);
  }

  @TestTemplate
  public void shouldDeleteGlobalListener(final CamundaRdbmsTestApplication testApplication) {
    // given an existing global listener
    final GlobalListenerDbModel globalListener =
        createAndSaveRandomGlobalListener(testApplication, b -> b.type("old-job-type"));

    // when deleting it (with the same listener id and listener type, regardless of changes in other
    // fields)
    final GlobalListenerDbModel deletedGlobalListener =
        createRandomGlobalListener(
            b ->
                b.listenerId(globalListener.listenerId())
                    .listenerType(globalListener.listenerType()));
    final RdbmsWriters rdbmsWriters = testApplication.getRdbmsService().createWriter(0);
    rdbmsWriters.getGlobalListenerWriter().delete(deletedGlobalListener);
    rdbmsWriters.flush();

    // then the listener is not available anymore
    final var instance =
        testApplication
            .getRdbmsService()
            .getGlobalListenerDbReader()
            .getGlobalListener(
                globalListener.listenerId(),
                globalListener.listenerType(),
                ResourceAccessChecks.disabled());
    assertThat(instance).isNull();
  }

  private void assertDbModelEqualToEntity(
      final GlobalListenerDbModel dbModel, final GlobalListenerEntity entity) {
    assertThat(entity).usingRecursiveComparison().isEqualTo(dbModel);
  }
}
