/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.batchoperation;

import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.createAndSaveBatchOperation;
import static io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures.createAndSaveRandomBatchOperationItems;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.BatchOperationItemDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemEntity;
import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.BatchOperationItemQuery;
import io.camunda.search.sort.BatchOperationItemSort;
import io.camunda.util.ObjectBuilder;
import java.util.Comparator;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class BatchOperationItemSortIT {

  public static final Long PARTITION_ID = 0L;

  @TestTemplate
  public void shouldSortByBatchOperationKey(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.batchOperationKey().asc(),
        Comparator.comparing(BatchOperationItemEntity::batchOperationKey));
  }

  @TestTemplate
  public void shouldSortByItemKey(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.itemKey().asc(),
        Comparator.comparing(BatchOperationItemEntity::itemKey));
  }

  @TestTemplate
  public void shouldSortByProcessInstanceKey(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.processInstanceKey().asc(),
        Comparator.comparing(BatchOperationItemEntity::processInstanceKey));
  }

  @TestTemplate
  public void shouldSortByState(final CamundaRdbmsTestApplication testApplication) {
    testSorting(
        testApplication.getRdbmsService(),
        b -> b.state().asc(),
        Comparator.comparing(BatchOperationItemEntity::state));
  }

  private void testSorting(
      final RdbmsService rdbmsService,
      final Function<BatchOperationItemSort.Builder, ObjectBuilder<BatchOperationItemSort>>
          sortBuilder,
      final Comparator<BatchOperationItemEntity> comparator) {
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final BatchOperationItemDbReader reader = rdbmsService.getBatchOperationItemReader();

    final var batchOperation = createAndSaveBatchOperation(rdbmsWriter, b -> b);

    createAndSaveRandomBatchOperationItems(rdbmsWriter, batchOperation.batchOperationKey(), 20);

    final var searchResult =
        reader
            .search(
                new BatchOperationItemQuery(
                    new BatchOperationItemFilter.Builder()
                        .batchOperationKeys(batchOperation.batchOperationKey())
                        .build(),
                    BatchOperationItemSort.of(sortBuilder),
                    SearchQueryPage.of(b -> b)))
            .items();

    assertThat(searchResult).hasSize(20);
    assertThat(searchResult).isSortedAccordingTo(comparator);
  }
}
