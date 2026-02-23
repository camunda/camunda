/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.NOW;
import static io.camunda.it.rdbms.db.fixtures.CommonFixtures.randomEnum;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel;
import io.camunda.db.rdbms.write.domain.BatchOperationDbModel.Builder;
import io.camunda.db.rdbms.write.domain.BatchOperationItemDbModel;
import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationItemState;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
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
            .state(randomEnum(BatchOperationState.class))
            .operationType(BatchOperationType.CANCEL_PROCESS_INSTANCE)
            .startDate(OffsetDateTime.now())
            .endDate(OffsetDateTime.now().plusSeconds(1))
            .actorType(randomEnum(AuditLogActorType.class))
            .actorId("actor-" + key)
            .operationsTotalCount(0)
            .operationsFailedCount(0)
            .operationsCompletedCount(0);
    return builderFunction.apply(builder).build();
  }

  public static List<BatchOperationDbModel> createAndSaveRandomBatchOperations(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    return createAndSaveRandomBatchOperations(rdbmsWriters, 20, builderFunction);
  }

  public static List<BatchOperationDbModel> createAndSaveRandomBatchOperations(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    final List<BatchOperationDbModel> models =
        IntStream.range(0, numberOfInstances)
            .mapToObj(i -> createRandomized(builderFunction))
            .peek(rdbmsWriters.getBatchOperationWriter()::createIfNotAlreadyExists)
            .collect(Collectors.toList());
    rdbmsWriters.flush();
    return models;
  }

  public static List<BatchOperationItemDbModel> createAndSaveRandomBatchOperationItems(
      final RdbmsWriters rdbmsWriters, final String batchOperationKey, final int count) {
    return createSaveReturnRandomBatchOperationItems(rdbmsWriters, batchOperationKey, count);
  }

  public static List<BatchOperationItemDbModel> createSaveReturnRandomBatchOperationItems(
      final RdbmsWriters rdbmsWriters, final String batchOperationKey, final int count) {
    final Set<Long> itemKeys =
        IntStream.range(0, count)
            .mapToObj(i -> CommonFixtures.nextKey())
            .collect(Collectors.toSet());
    return insertBatchOperationsItems(rdbmsWriters, batchOperationKey, itemKeys);
  }

  public static BatchOperationDbModel createAndSaveBatchOperation(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var instance = createRandomized(builderFunction);
    createAndSaveBatchOperations(rdbmsWriters, List.of(instance));
    return instance;
  }

  public static void createAndSaveBatchOperations(
      final RdbmsWriters rdbmsWriters, final List<BatchOperationDbModel> batchOperationList) {
    for (final BatchOperationDbModel batchOperation : batchOperationList) {
      rdbmsWriters.getBatchOperationWriter().createIfNotAlreadyExists(batchOperation);
    }
    rdbmsWriters.flush();
  }

  public static List<BatchOperationItemDbModel> insertBatchOperationsItems(
      final RdbmsWriters rdbmsWriters, final String batchOperationKey, final Set<Long> itemKeys) {
    final var batchItems =
        itemKeys.stream()
            .map(
                itemKey ->
                    new BatchOperationItemDbModel(
                        batchOperationKey,
                        itemKey,
                        itemKey + 10_000L, // process instance key
                        itemKey + 20_000L, // root process instance key
                        BatchOperationItemState.ACTIVE,
                        NOW,
                        null))
            .toList();

    rdbmsWriters.getBatchOperationWriter().updateBatchAndInsertItems(batchOperationKey, batchItems);
    rdbmsWriters.flush();

    return batchItems;
  }
}
