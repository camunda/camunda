/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.BatchOperationReader;
import io.camunda.it.rdbms.db.fixtures.BatchOperationFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.BatchOperationEntity;
import io.camunda.search.filter.BatchOperationFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.BatchOperationQuery;
import io.camunda.search.sort.BatchOperationSort;
import java.util.Comparator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class BatchOperationSortIT {

  @TestTemplate
  public void shouldSortIdAsc(final CamundaRdbmsTestApplication testApplication) {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    final BatchOperationReader batchOperationReader = rdbmsService.getBatchOperationReader();

    BatchOperationFixtures.createAndSaveRandomBatchOperations(
        rdbmsService.createWriter(1L), b -> b);

    final var searchResult =
        batchOperationReader.search(
            BatchOperationQuery.of(
                b ->
                    b.filter(new BatchOperationFilter.Builder().build())
                        .sort(BatchOperationSort.of(s -> s.batchOperationId().asc()))
                        .page(SearchQueryPage.of(p -> p.from(0).size(10)))));

    assertThat(searchResult.items())
        .isSortedAccordingTo(Comparator.comparing(BatchOperationEntity::batchOperationId));
  }
}
