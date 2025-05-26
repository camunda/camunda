/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.RANDOM;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.randomEnum;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel.Builder;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class BatchOperationFixtures {

  private BatchOperationFixtures() {}

  public static BatchOperationDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var key = CommonFixtures.nextStringKey();
    final var builder =
        new Builder()
            .batchOperationKey(key)
            .partitionId(1)
            .state(randomEnum(BatchOperationState.class))
            .operationType("some-operation" + RANDOM.nextInt(1000))
            .startDate(OffsetDateTime.now())
            .endDate(OffsetDateTime.now().plusSeconds(1))
            .operationsTotalCount(RANDOM.nextInt(1000))
            .operationsFailedCount(RANDOM.nextInt(1000))
            .operationsCompletedCount(RANDOM.nextInt(1000));
    return builderFunction.apply(builder).build();
  }

  public static List<BatchOperationDbModel> createAndSaveRandomBatchOperations(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final List<BatchOperationDbModel> models =
        IntStream.range(0, 20)
            .mapToObj(i -> createRandomized(builderFunction))
            .peek(rdbmsWriter.getBatchOperationWriter()::create)
            .collect(Collectors.toList());
    rdbmsWriter.flush();
    return models;
  }

  public static List<Long> createAndSaveRandomBatchOperationItems(
      final RdbmsWriter rdbmsWriter, final String batchOperationKey, final int count) {
    final Set<Long> itemKeys =
        IntStream.range(0, count)
            .mapToObj(i -> CommonFixtures.nextKey())
            .collect(Collectors.toSet());
    insertBatchOperationsItems(rdbmsWriter, batchOperationKey, itemKeys);
    return itemKeys.stream().toList();
  }

  public static BatchOperationDbModel createAndSaveBatchOperation(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var instance = createRandomized(builderFunction);
    createAndSaveBatchOperations(rdbmsWriter, List.of(instance));
    return instance;
  }

  public static void createAndSaveBatchOperations(
      final RdbmsWriter rdbmsWriter, final List<BatchOperationDbModel> batchOperationList) {
    for (final BatchOperationDbModel batchOperation : batchOperationList) {
      rdbmsWriter.getBatchOperationWriter().create(batchOperation);
    }
    rdbmsWriter.flush();
  }

  public static void insertBatchOperationsItems(
      final RdbmsWriter rdbmsWriter, final String batchOperationKey, final Set<Long> items) {
    rdbmsWriter
        .getBatchOperationWriter()
        .updateBatchAndInsertItems(
            batchOperationKey,
            1,
            items.stream()
                .map(
                    itemKey ->
                        new BatchOperationItemDbModel(
                            batchOperationKey, itemKey, itemKey, BatchOperationItemState.ACTIVE))
                .toList());
    rdbmsWriter.flush();
  }
}
