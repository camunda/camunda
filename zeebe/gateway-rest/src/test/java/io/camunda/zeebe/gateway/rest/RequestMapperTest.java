/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.model.mapper.RequestMapper;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.MigrateProcessInstanceMappingInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class RequestMapperTest {

  @Test
  void shouldMapToProcessInstanceMigrationBatchOperationRequest() {
    // given
    final var migrationPlan = new ProcessInstanceMigrationBatchOperationPlan();
    migrationPlan.setTargetProcessDefinitionKey("123");
    final var mappingInstruction = new MigrateProcessInstanceMappingInstruction();
    mappingInstruction.setSourceElementId("source1");
    mappingInstruction.setTargetElementId("target1");
    migrationPlan.setMappingInstructions(List.of(mappingInstruction));

    final var batchOperationInstruction = new ProcessInstanceMigrationBatchOperationRequest();
    batchOperationInstruction.setMigrationPlan(migrationPlan);
    final var filter = new ProcessInstanceFilter();
    filter.setProcessDefinitionId(new AdvancedStringFilter().$like("process"));
    batchOperationInstruction.setFilter(filter);

    // when
    final Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest> result =
        RequestMapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationInstruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.targetProcessDefinitionKey()).isEqualTo(123L);
    assertThat(request.mappingInstructions())
        .hasSize(1)
        .first()
        .satisfies(
            instruction -> {
              assertThat(instruction.getSourceElementId()).isEqualTo("source1");
              assertThat(instruction.getTargetElementId()).isEqualTo("target1");
            });
  }

  @Test
  void shouldReturnProblemDetailForInvalidInput() {
    // given
    final var migrationPlan = new ProcessInstanceMigrationBatchOperationPlan();
    migrationPlan.setTargetProcessDefinitionKey("123");
    final var mappingInstruction = new MigrateProcessInstanceMappingInstruction();
    mappingInstruction.setSourceElementId(null);
    mappingInstruction.setTargetElementId(null);
    migrationPlan.setMappingInstructions(List.of(mappingInstruction));

    final var batchOperationRequest = new ProcessInstanceMigrationBatchOperationRequest();
    batchOperationRequest.setMigrationPlan(migrationPlan);
    final var filter = new ProcessInstanceFilter();
    batchOperationRequest.setFilter(filter);

    // when
    final Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest> result =
        RequestMapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationRequest);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400); // Bad Request
    assertThat(problemDetail.getDetail()).contains("are required");
  }

  @Test
  void shouldMapProcessInstanceModifyBatchOperationRequest() {
    // given
    final var moveInstruction =
        new ProcessInstanceModificationMoveBatchOperationInstruction()
            .sourceElementId("source1")
            .targetElementId("target1");
    final var filter = new ProcessInstanceFilter();
    filter.setProcessDefinitionId(new AdvancedStringFilter().$like("process"));

    final var modificationRequest =
        new ProcessInstanceModificationBatchOperationRequest()
            .addMoveInstructionsItem(moveInstruction);
    modificationRequest.setFilter(filter);

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        RequestMapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.moveInstructions())
        .hasSize(1)
        .first()
        .satisfies(
            instruction -> {
              assertThat(instruction.getSourceElementId()).isEqualTo("source1");
              assertThat(instruction.getTargetElementId()).isEqualTo("target1");
              assertThat(instruction.getAncestorScopeKey()).isEqualTo(-1L);
              assertThat(instruction.isInferAncestorScopeFromSourceHierarchy()).isTrue();
              assertThat(instruction.getVariableInstructions()).isEmpty();
            });
  }

  @Test
  void shouldNotMapProcessInstanceModifyBatchOperationRequestWhenInvalid() {
    // given
    final var moveInstruction =
        new ProcessInstanceModificationMoveBatchOperationInstruction().sourceElementId("source1");
    final var filter = new ProcessInstanceFilter();
    filter.setProcessDefinitionId(new AdvancedStringFilter().$like("process"));

    final var modificationRequest =
        new ProcessInstanceModificationBatchOperationRequest()
            .addMoveInstructionsItem(moveInstruction);
    modificationRequest.setFilter(filter);

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        RequestMapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("No targetElementId provided.");
  }
}
