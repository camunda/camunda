/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.OperationExecutorProperties;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ModifyProcessInstanceHandlerTest {

  private static final String WORKER_ID = "testWorker";

  @Mock private OperateServicesAdapter operateServicesAdapter;
  @Mock private BatchOperationWriter batchOperationWriter;

  @Mock(strictness = Strictness.LENIENT)
  private OperateProperties operateProperties;

  @Mock private Metrics metrics;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private ModifyProcessInstanceHandler handler;

  @BeforeEach
  void setup() {
    final var executorProps = new OperationExecutorProperties();
    executorProps.setWorkerId(WORKER_ID);
    when(operateProperties.getOperationExecutor()).thenReturn(executorProps);
  }

  @Test
  void shouldFailInsteadOfRetryWhenCommandOutcomeIsUnknown() throws Exception {
    // given
    final var operation = createLockedOperation();
    final var timeout = new RuntimeException("deadline exceeded");
    doThrow(timeout)
        .when(operateServicesAdapter)
        .modifyProcessInstance(anyLong(), any(), anyString());
    when(operateServicesAdapter.isExceptionRetriable(timeout)).thenReturn(true);
    when(operateServicesAdapter.isOutcomeUnknown(timeout)).thenReturn(true);

    // when
    handler.handle(operation);

    // then
    verify(batchOperationWriter).updateOperation(operation);
    assertThat(operation.getState()).isEqualTo(OperationState.FAILED);
  }

  @Test
  void shouldRetryWhenCommandWasRejectedBeforeProcessing() throws Exception {
    // given
    final var operation = createLockedOperation();
    final var rejected = new RuntimeException("resource exhausted");
    doThrow(rejected)
        .when(operateServicesAdapter)
        .modifyProcessInstance(anyLong(), any(), anyString());
    when(operateServicesAdapter.isExceptionRetriable(rejected)).thenReturn(true);
    when(operateServicesAdapter.isOutcomeUnknown(rejected)).thenReturn(false);

    // when
    handler.handle(operation);

    // then
    verify(batchOperationWriter, never()).updateOperation(any());
    assertThat(operation.getState()).isEqualTo(OperationState.LOCKED);
  }

  private OperationEntity createLockedOperation() throws Exception {
    final var request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(List.of());
    return new OperationEntity()
        .setId("456")
        .setType(OperationType.MODIFY_PROCESS_INSTANCE)
        .setModifyInstructions(objectMapper.writeValueAsString(request))
        .setState(OperationState.LOCKED)
        .setLockOwner(WORKER_ID);
  }
}
