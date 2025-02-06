/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.tenant;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextKey;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.nextStringId;
import static io.camunda.it.rdbms.db.fixtures.TenantFixtures.createAndSaveRandomTenants;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.TenantReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.TenantDbModel;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.TenantQuery;
import io.camunda.search.sort.TenantSort;
import io.camunda.search.sort.TenantSort.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class TenantSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByTenantKeyAsc(final CamundaRdbmsTestApplication testApplication) {
    final var aggregator =
        "AggregatorSortByKeyAsc " + nextStringId(); // Will be used to have isolated test data

    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantKey().asc(),
        Comparator.comparing(TenantEntity::key),
        b -> b.name(aggregator),
        b -> b.name(aggregator));
  }

  @TestTemplate
  public void shouldSortByTenantKeyDesc(final CamundaRdbmsTestApplication testApplication) {
    final var aggregator =
        "AggregatorSortByKeyDesc " + nextStringId(); // Will be used to have isolated test data

    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantKey().desc(),
        Comparator.comparing(TenantEntity::key).reversed(),
        b -> b.name(aggregator),
        b -> b.name(aggregator));
  }

  @TestTemplate
  public void shouldSortByTenantIdAsc(final CamundaRdbmsTestApplication testApplication) {
    final var aggregator =
        "AggregatorSortByKeyAsc " + nextStringId(); // Will be used to have isolated test data

    testSorting(
        testApplication.getRdbmsService(),
        b -> b.tenantId().asc(),
        Comparator.comparing(TenantEntity::tenantId),
        b -> b.name(aggregator),
        b -> b.name(aggregator));
  }

  @TestTemplate
  public void shouldSortByNameAsc(final CamundaRdbmsTestApplication testApplication) {
    final var aggregator = nextKey(); // Will be used to have isolated test data

    testSorting(
        testApplication.getRdbmsService(),
        b -> b.name().asc(),
        Comparator.comparing(TenantEntity::name),
        b -> b.tenantKey(aggregator),
        b -> b.key(aggregator));
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<Builder, ObjectBuilder<TenantSort>> sortBuilder,
      final Comparator<TenantEntity> comparator,
      final Function<TenantDbModel.Builder, TenantDbModel.Builder> aggregatorBuilderFunction,
      final Function<TenantFilter.Builder, TenantFilter.Builder> aggregatorFilterFunction) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final TenantReader reader = rdbmsService.getTenantReader();

    aggregatorBuilderFunction.apply(new TenantDbModel.Builder().name("test"));
    createAndSaveRandomTenants(rdbmsWriter, aggregatorBuilderFunction);

    final var searchResult =
        reader
            .search(
                new TenantQuery(
                    TenantFilter.of(aggregatorFilterFunction),
                    TenantSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSizeGreaterThanOrEqualTo(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
