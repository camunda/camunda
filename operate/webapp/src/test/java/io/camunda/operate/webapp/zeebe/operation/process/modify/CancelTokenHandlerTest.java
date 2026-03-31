/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CancelTokenHandlerTest {
  @Mock private FlowNodeInstanceReader mockFlowNodeInstanceReader;

  private CancelTokenHandler cancelTokenHandler;

  private ModifyProcessInstanceCommandStep1 mockZeebeCommand;

  @BeforeEach
  public void setup() {
    cancelTokenHandler =
        new CancelTokenHandler(mockFlowNodeInstanceReader, new OperateProperties());

    mockZeebeCommand =
        Mockito.mock(
            ModifyProcessInstanceCommandStep1.class,
            withSettings()
                .extraInterfaces(
                    ModifyProcessInstanceCommandStep2.class,
                    ModifyProcessInstanceCommandStep3.class));

    when(mockZeebeCommand.terminateElement(anyLong()))
        .thenReturn((ModifyProcessInstanceCommandStep2) mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep2) mockZeebeCommand).and()).thenReturn(mockZeebeCommand);
  }

  @Test
  public void testCancelTokenByInstanceKey() {
    // given
    final Modification modification =
        new Modification().setModification(Type.CANCEL_TOKEN).setFromFlowNodeInstanceKey("456");

    // when
    final ModifyProcessInstanceCommandStep2 result =
        cancelTokenHandler.cancelToken(mockZeebeCommand, 123L, modification);

    // then
    assertThat(result).isNotNull();
    verify(mockZeebeCommand, times(1)).terminateElement(456L);
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testCancelTokenByFlowNodeId() {
    // given
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(100L, 200L));

    // when
    final Modification modification =
        new Modification().setModification(Type.CANCEL_TOKEN).setFromFlowNodeId("taskA");

    final ModifyProcessInstanceCommandStep2 result =
        cancelTokenHandler.cancelToken(mockZeebeCommand, 123L, modification);

    // then
    assertThat(result).isNotNull();
    verify(mockZeebeCommand, times(1)).terminateElement(100L);
    verify(mockZeebeCommand, times(1)).terminateElement(200L);
    verify(mockFlowNodeInstanceReader, times(1))
        .getFlowNodeInstanceKeysByIdAndStates(123L, "taskA", List.of(FlowNodeState.ACTIVE));
  }

  @Test
  public void testCancelTokenThrowsWhenNoInstancesFound() {
    // given
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            anyLong(), anyString(), Mockito.anyList()))
        .thenReturn(List.of());

    // when - then
    final Modification modification =
        new Modification().setModification(Type.CANCEL_TOKEN).setFromFlowNodeId("taskA");

    assertThatThrownBy(() -> cancelTokenHandler.cancelToken(mockZeebeCommand, 123L, modification))
        .isInstanceOf(OperateRuntimeException.class)
        .hasMessageContaining("Abort CANCEL_TOKEN");

    verify(mockZeebeCommand, never()).terminateElement(anyLong());
  }

  @Test
  public void testCancelTokenLimitedByMaxLimit() {
    // given
    final int testLimit = 5;
    final OperateProperties operateProperties = new OperateProperties();
    operateProperties.getOperationExecutor().setMaxModifyTokensLimit(testLimit);
    final CancelTokenHandler limitedHandler =
        new CancelTokenHandler(mockFlowNodeInstanceReader, operateProperties);

    final List<Long> allKeys = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(allKeys);

    // when
    final Modification modification =
        new Modification().setModification(Type.CANCEL_TOKEN).setFromFlowNodeId("taskA");

    final ModifyProcessInstanceCommandStep2 result =
        limitedHandler.cancelToken(mockZeebeCommand, 123L, modification);

    // then
    assertThat(result).isNotNull();
    verify(mockZeebeCommand, times(testLimit)).terminateElement(anyLong());
    verify(mockZeebeCommand, never()).terminateElement(6L);
  }
}
