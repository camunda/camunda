/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import java.util.List;
import java.util.Map;
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
public class MoveTokenHandlerTest {
  @Mock private FlowNodeInstanceReader mockFlowNodeInstanceReader;

  private MoveTokenHandler moveTokenHandler;

  private ModifyProcessInstanceCommandStep1 mockZeebeCommand;

  @BeforeEach
  public void setup() {
    moveTokenHandler = new MoveTokenHandler(mockFlowNodeInstanceReader);

    mockZeebeCommand =
        Mockito.mock(
            ModifyProcessInstanceCommandStep1.class,
            withSettings()
                .extraInterfaces(
                    ModifyProcessInstanceCommandStep2.class,
                    ModifyProcessInstanceCommandStep3.class));

    when(mockZeebeCommand.activateElement(anyString()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(mockZeebeCommand.activateElement(anyString(), anyLong()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(mockZeebeCommand.terminateElement(anyLong()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep2) mockZeebeCommand).and()).thenReturn(mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep3) mockZeebeCommand)
            .withVariables(anyMap(), anyString()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
  }

  @Test
  public void testZeroNewTokensDeclared() {
    final Modification modification =
        new Modification()
            .setNewTokensCount(0)
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    assertThat(moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification)).isNull();
    verifyNoInteractions(mockZeebeCommand);
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testZeroNewTokensCalculated() {
    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of());

    assertThat(moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification)).isNull();
    verifyNoInteractions(mockZeebeCommand);
    verify(mockFlowNodeInstanceReader, times(1))
        .getFlowNodeInstanceKeysByIdAndStates(
            123L, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE));
  }

  @Test
  public void testNoFromFlowNodeSpecified() {
    final Modification modification =
        new Modification().setModification(Type.MOVE_TOKEN).setToFlowNodeId("taskB");

    assertThat(moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification)).isNull();
    verifyNoInteractions(mockZeebeCommand);
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testMoveToken() {
    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeInstanceKey("888")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(3);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(888L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testMoveTokenWithVariables() {
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(456L));

    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB")
            .setVariables(
                Map.of(
                    "taskB", List.of(Map.of("a", "b")),
                    "process", List.of(Map.of("c", "d"))));

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(5);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");
    verify((ModifyProcessInstanceCommandStep3) mockZeebeCommand, times(1))
        .withVariables(Map.of("c", "d"), "process");
    verify((ModifyProcessInstanceCommandStep3) mockZeebeCommand, times(1))
        .withVariables(Map.of("a", "b"), "taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(456L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    verify(mockFlowNodeInstanceReader, times(2))
        .getFlowNodeInstanceKeysByIdAndStates(123L, "taskA", List.of(FlowNodeState.ACTIVE));
  }

  @Test
  public void testMoveMultipleTokens() {
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(456L, 789L));

    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(7);
    verify(mockZeebeCommand, times(2)).activateElement("taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(456L);
    verify(mockZeebeCommand, times(1)).terminateElement(789);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(3)).and();
  }

  @Test
  public void testMoveTokenWithAncestor() {
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(456L));

    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setAncestorElementInstanceKey(999L)
            .setFromFlowNodeInstanceKey("888")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(3);
    verify(mockZeebeCommand, times(1)).activateElement("taskB", 999L);
    verify(mockZeebeCommand, times(1)).terminateElement(888L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testMoveTokenWithIdAndInstanceKeySpecified() {
    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setFromFlowNodeInstanceKey("888")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(3);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(888L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    // Since an instance key was specified, reader should not be called during token count or
    // cancelling existing tokens
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }
}
