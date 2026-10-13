/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static io.camunda.operate.util.TestUtil.createOperationEntity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.OperationReader.VersionedOperation;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

public class BatchOperationWriterLockIT extends OperateSearchAbstractIT {

  @Autowired private BatchOperationWriter batchOperationWriter;
  @Autowired private OperationTemplate operationTemplate;
  @Autowired private OperateProperties operateProperties;
  @MockitoSpyBean private OperationReader operationReader;

  @Test
  public void shouldLockScheduledOperationOnlyOnceWhenWorkersAcquireItConcurrently()
      throws Exception {
    // given
    final OperationEntity operation = persistOperation(OperationState.SCHEDULED, false);
    givenTwoWorkersAcquiredOperationBeforeEitherLockedIt(operation);

    // when
    final List<OperationEntity> lockedByFirstWorker = batchOperationWriter.lockBatch();
    final List<OperationEntity> lockedBySecondWorker = batchOperationWriter.lockBatch();

    // then
    assertThat(lockedByFirstWorker).extracting(OperationEntity::getId).contains(operation.getId());
    assertThat(lockedBySecondWorker)
        .extracting(OperationEntity::getId)
        .doesNotContain(operation.getId());
  }

  @Test
  public void shouldLockExpiredOperationOnlyOnceWhenWorkersAcquireItConcurrently()
      throws Exception {
    // given
    final OperationEntity operation = persistOperation(OperationState.LOCKED, true);
    givenTwoWorkersAcquiredOperationBeforeEitherLockedIt(operation);

    // when
    final List<OperationEntity> lockedByFirstWorker = batchOperationWriter.lockBatch();
    final List<OperationEntity> lockedBySecondWorker = batchOperationWriter.lockBatch();

    // then
    assertThat(lockedByFirstWorker).extracting(OperationEntity::getId).contains(operation.getId());
    assertThat(lockedBySecondWorker)
        .extracting(OperationEntity::getId)
        .doesNotContain(operation.getId());
  }

  @Test
  public void shouldPersistLockOfAcquiredOperation() throws Exception {
    // given
    final OperationEntity operation = persistOperation(OperationState.SCHEDULED, false);

    // when
    batchOperationWriter.lockBatch();

    // then
    final OperationEntity persisted =
        testSearchRepository
            .searchTerm(
                operationTemplate.getFullQualifiedName(),
                OperationTemplate.ID,
                operation.getId(),
                OperationEntity.class,
                1)
            .getFirst();
    assertThat(persisted.getState()).isEqualTo(OperationState.LOCKED);
    assertThat(persisted.getLockOwner())
        .isEqualTo(operateProperties.getOperationExecutor().getWorkerId());
    assertThat(persisted.getLockExpirationTime()).isNotNull();
    assertThat(persisted.getType()).isEqualTo(operation.getType());
  }

  @Test
  public void shouldFailBatchWhenOperationCannotBeLockedForOtherReasonThanConflict()
      throws Exception {
    // given
    final OperationEntity operation = persistOperation(OperationState.SCHEDULED, false);
    final List<VersionedOperation> acquired =
        acquiredOnly(
            operation,
            operationReader.acquireOperations(
                operateProperties.getOperationExecutor().getBatchSize()));
    doReturn(acquired).when(operationReader).acquireOperations(anyInt());
    testSearchRepository.deleteById(operationTemplate.getFullQualifiedName(), operation.getId());
    searchContainerManager.refreshIndices("*operation*");

    // when / then
    assertThatThrownBy(() -> batchOperationWriter.lockBatch())
        .isInstanceOf(PersistenceException.class)
        .hasMessageContaining(operation.getId());
  }

  private OperationEntity persistOperation(final OperationState state, final boolean lockExpired)
      throws Exception {
    final long processInstanceKey = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    final OperationEntity operation = createOperationEntity(processInstanceKey, state, lockExpired);
    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), operation.getId(), operation);
    searchContainerManager.refreshIndices("*operation*");
    return operation;
  }

  private void givenTwoWorkersAcquiredOperationBeforeEitherLockedIt(
      final OperationEntity operation) {
    final int batchSize = operateProperties.getOperationExecutor().getBatchSize();
    final List<VersionedOperation> acquiredByFirstWorker =
        acquiredOnly(operation, operationReader.acquireOperations(batchSize));
    final List<VersionedOperation> acquiredBySecondWorker =
        acquiredOnly(operation, operationReader.acquireOperations(batchSize));
    assertThat(acquiredByFirstWorker).hasSize(1);
    assertThat(acquiredBySecondWorker).hasSize(1);

    doReturn(acquiredByFirstWorker)
        .doReturn(acquiredBySecondWorker)
        .when(operationReader)
        .acquireOperations(anyInt());
  }

  private static List<VersionedOperation> acquiredOnly(
      final OperationEntity operation, final List<VersionedOperation> acquired) {
    return acquired.stream()
        .filter(candidate -> candidate.operation().getId().equals(operation.getId()))
        .toList();
  }
}
