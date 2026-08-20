/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.zeebe.gateway.RequestMapper;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.ActivateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.MoveInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.TerminateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import org.junit.jupiter.api.Test;

public class ModifyProcessInstanceTest {

  private static final String VALID_VARIABLES = "{ \"test\": \"value\"}";
  // missing closing quote in second variable
  private static final String VALID_MOVE_VARIABLES = "{ \"testMove\": \"valueMove\"}";
  private static final String INVALID_VARIABLES =
      "{ \"test\": \"value\", \"error\": \"errorrvalue }";

  @Test
  public void shouldMapRequestToModifyProcessInstanceRequest() {
    // given
    final var request =
        ModifyProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(1L)
            .addActivateInstructions(
                ActivateInstruction.newBuilder()
                    .setElementId("elementId")
                    .setAncestorElementInstanceKey(2L)
                    .addVariableInstructions(
                        VariableInstruction.newBuilder()
                            .setScopeId("scopeId")
                            .setVariables(VALID_VARIABLES)
                            .build())
                    .build())
            .addTerminateInstructions(
                TerminateInstruction.newBuilder()
                    .setElementInstanceKey(3L)
                    .setElementId("elementId")
                    .build())
            .addMoveInstructions(
                MoveInstruction.newBuilder()
                    .setSourceElementId("fromElementId")
                    .setTargetElementId("toElementId")
                    .setAncestorElementInstanceKey(3L)
                    .setInferAncestorScopeFromSourceHierarchy(true)
                    .addVariableInstructions(
                        VariableInstruction.newBuilder()
                            .setScopeId("scopeIdMove")
                            .setVariables(VALID_MOVE_VARIABLES)
                            .build())
                    .build())
            .addMoveInstructions(
                MoveInstruction.newBuilder()
                    .setSourceElementInstanceKey(123L)
                    .setTargetElementId("toElementId")
                    .setAncestorElementInstanceKey(3L)
                    .setInferAncestorScopeFromSourceHierarchy(true)
                    .addVariableInstructions(
                        VariableInstruction.newBuilder()
                            .setScopeId("scopeIdMove")
                            .setVariables(VALID_MOVE_VARIABLES)
                            .build())
                    .build())
            .build();

    // when
    final ProcessInstanceModificationRecord record =
        RequestMapper.toModifyProcessInstanceRequest(request).getRequestWriter();

    // then
    assertThat(record.getProcessInstanceKey()).isEqualTo(1L);
    final var activateInstructions = record.getActivateInstructions();
    assertThat(activateInstructions).hasSize(1);
    final var activateInstruction = activateInstructions.get(0);
    assertThat(activateInstruction.getElementId()).isEqualTo("elementId");
    assertThat(activateInstruction.getAncestorScopeKey()).isEqualTo(2L);
    final var variableInstructions = activateInstruction.getVariableInstructions();
    assertThat(variableInstructions).hasSize(1);
    final var variableInstruction = variableInstructions.get(0);
    assertThat(variableInstruction.getElementId()).isEqualTo("scopeId");
    assertThat(variableInstruction.getVariables()).containsOnly(entry("test", "value"));
    final var terminateInstructions = record.getTerminateInstructions();
    assertThat(terminateInstructions).hasSize(1);
    final var terminateInstruction = terminateInstructions.get(0);
    assertThat(terminateInstruction.getElementInstanceKey()).isEqualTo(3L);
    assertThat(terminateInstruction.getElementId()).isEqualTo("elementId");
    final var moveInstructions = record.getMoveInstructions();
    assertThat(moveInstructions).hasSize(2);
    final var moveInstruction1 = moveInstructions.get(0);
    assertThat(moveInstruction1.getSourceElementId()).isEqualTo("fromElementId");
    assertThat(moveInstruction1.getTargetElementId()).isEqualTo("toElementId");
    assertThat(moveInstruction1.getAncestorScopeKey()).isEqualTo(3L);
    assertThat(moveInstruction1.isInferAncestorScopeFromSourceHierarchy()).isTrue();
    final var moveVariableInstructions1 = moveInstruction1.getVariableInstructions();
    assertThat(moveVariableInstructions1).hasSize(1);
    final var moveVariableInstruction1 = moveVariableInstructions1.get(0);
    assertThat(moveVariableInstruction1.getElementId()).isEqualTo("scopeIdMove");
    assertThat(moveVariableInstruction1.getVariables())
        .containsOnly(entry("testMove", "valueMove"));
    final var moveInstruction2 = moveInstructions.get(1);
    assertThat(moveInstruction2.getSourceElementInstanceKey()).isEqualTo(123L);
    assertThat(moveInstruction2.getTargetElementId()).isEqualTo("toElementId");
    assertThat(moveInstruction2.getAncestorScopeKey()).isEqualTo(3L);
    assertThat(moveInstruction2.isInferAncestorScopeFromSourceHierarchy()).isTrue();
    final var moveVariableInstructions2 = moveInstruction2.getVariableInstructions();
    assertThat(moveVariableInstructions2).hasSize(1);
    final var moveVariableInstruction2 = moveVariableInstructions2.get(0);
    assertThat(moveVariableInstruction2.getElementId()).isEqualTo("scopeIdMove");
    assertThat(moveVariableInstruction2.getVariables())
        .containsOnly(entry("testMove", "valueMove"));
  }

  @Test
  public void shouldThrowExceptionOnToModifyProcessInstanceRequestWithInvalidVariables() {
    // given
    final var request =
        ModifyProcessInstanceRequest.newBuilder()
            .setProcessInstanceKey(1L)
            .addActivateInstructions(
                ActivateInstruction.newBuilder()
                    .setElementId("elementId")
                    .setAncestorElementInstanceKey(2L)
                    .addVariableInstructions(
                        VariableInstruction.newBuilder()
                            .setScopeId("scopeId")
                            .setVariables(INVALID_VARIABLES)
                            .build())
                    .build())
            .addTerminateInstructions(
                TerminateInstruction.newBuilder().setElementInstanceKey(3L).build())
            .build();

    // when + then
    assertThatThrownBy(() -> RequestMapper.toModifyProcessInstanceRequest(request))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", INVALID_VARIABLES)
        .cause()
        .isInstanceOf(JsonParseException.class);
  }
}
