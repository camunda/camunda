/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pins down the expected behavior for the "stale process definition after delete" bug (INC-34483):
 * if {@link ProcessStore#deleteProcessDefinitionsByKeys} removes nothing from the secondary storage
 * index (e.g. because a version conflict aborted the underlying delete-by-query), the operation
 * must not be reported as completed; and once the engine has already deleted the definition, a
 * retry must still attempt the index cleanup that would repair it, since a NOT_FOUND rejection
 * means the engine is already in the desired state.
 *
 * <p>Both tests currently fail against {@link DeleteProcessDefinitionHandler} - they are written
 * against the fix, not the current (buggy) behavior.
 */
@ExtendWith(MockitoExtension.class)
class DeleteProcessDefinitionHandlerTest {

  private static final Long PROCESS_DEFINITION_KEY = 2251799813685313L;
  private static final String WORKER_ID = "test-worker";

  @Mock private OperationsManager operationsManager;
  @Mock private ProcessReader processReader;
  @Mock private ProcessStore processStore;
  @Mock private ListViewTemplate listViewTemplate;
  @Mock private OperateServicesAdapter operationServicesAdapter;
  @Mock private BatchOperationWriter batchOperationWriter;
  @Mock private Metrics metrics;

  private final OperateProperties operateProperties = new OperateProperties();
  private DeleteProcessDefinitionHandler underTest;

  @BeforeEach
  void setUp() {
    final OperationExecutorProperties executorProperties = new OperationExecutorProperties();
    executorProperties.setWorkerId(WORKER_ID);
    operateProperties.setOperationExecutor(executorProperties);

    underTest = new DeleteProcessDefinitionHandler();
    ReflectionTestUtils.setField(underTest, "operationsManager", operationsManager);
    // AbstractOperationHandler declares its own private "operationsManager" field, hiding this
    // one - failOperation() reads the superclass field, so it needs to be set explicitly too.
    ReflectionTestUtils.setField(
        underTest, AbstractOperationHandler.class, "operationsManager", operationsManager, null);
    ReflectionTestUtils.setField(underTest, "processReader", processReader);
    ReflectionTestUtils.setField(underTest, "processStore", processStore);
    ReflectionTestUtils.setField(underTest, "listViewTemplate", listViewTemplate);
    ReflectionTestUtils.setField(underTest, "operationServicesAdapter", operationServicesAdapter);
    ReflectionTestUtils.setField(underTest, "batchOperationWriter", batchOperationWriter);
    ReflectionTestUtils.setField(underTest, "operateProperties", operateProperties);
    ReflectionTestUtils.setField(underTest, "metrics", metrics);

    // no running/cancelled/completed instances -> cascadeDeleteProcessInstances is a no-op
    when(processStore.getProcessInstancesByProcessAndStates(
            eq(PROCESS_DEFINITION_KEY), any(), anyInt(), any()))
        .thenReturn(Collections.<ProcessInstanceForListViewEntity>emptyList());
  }

  private OperationEntity newLockedOperation() {
    final OperationEntity operation = new OperationEntity();
    operation.setId("op-1");
    operation.setBatchOperationId("batch-1");
    operation.setType(OperationType.DELETE_PROCESS_DEFINITION);
    operation.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    operation.setState(OperationState.LOCKED);
    operation.setLockOwner(WORKER_ID);
    return operation;
  }

  @Test
  void doesNotCompleteOperationWhenNothingWasDeletedFromTheIndex() throws Exception {
    // Given: the engine deletion succeeds, but the index cleanup removes zero documents -
    // e.g. because ElasticsearchProcessStore#deleteProcessDefinitionsByKeys hit a version
    // conflict with a concurrent exporter write and DeleteByQuery silently aborted.
    when(processStore.deleteProcessDefinitionsByKeys(PROCESS_DEFINITION_KEY)).thenReturn(0L);
    final OperationEntity operation = newLockedOperation();

    underTest.handleWithException(operation);

    // Expected: an operation that removed nothing from the index must not be reported as a
    // successful completion - the caller has no other way to learn the document is still there.
    verify(operationsManager, never()).completeOperation(operation);
  }

  @Test
  void retryAfterEngineAlreadyDeletedStillAttemptsIndexCleanup() throws Exception {
    // Given: this is a retry - the engine has already deleted the definition, so the delete
    // command is rejected with NOT_FOUND.
    doThrow(new RuntimeException("Command 'DELETE' rejected with code 'NOT_FOUND'"))
        .when(operationServicesAdapter)
        .deleteResource(eq(PROCESS_DEFINITION_KEY), any());
    final OperationEntity operation = newLockedOperation();

    // handle() is the entry point used by the operation executor.
    underTest.handle(operation);

    // Expected: NOT_FOUND means the engine is already in the state the operation wants, so the
    // handler should still attempt the index cleanup that a previous, conflicted attempt left
    // undone, and complete the operation once it succeeds - rather than failing outright and
    // leaving the stale document forever unreachable by further retries.
    verify(processStore, times(1)).deleteProcessDefinitionsByKeys(PROCESS_DEFINITION_KEY);
    verify(operationsManager, times(1)).completeOperation(operation);
  }
}
