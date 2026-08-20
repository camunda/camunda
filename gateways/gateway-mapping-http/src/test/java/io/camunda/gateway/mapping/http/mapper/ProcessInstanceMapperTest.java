/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import static org.assertj.core.api.Assertions.assertThat;

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

class ProcessInstanceMapperTest {

  private final ProcessInstanceMapper mapper = new ProcessInstanceMapper();

  @Test
  void shouldMapToProcessInstanceMigrationBatchOperationRequest() {
    // given
    final var mappingInstruction =
        MigrateProcessInstanceMappingInstruction.Builder.create()
            .sourceElementId("source1")
            .targetElementId("target1")
            .build();
    final var migrationPlan =
        ProcessInstanceMigrationBatchOperationPlan.Builder.create()
            .targetProcessDefinitionKey("123")
            .mappingInstructions(List.of(mappingInstruction))
            .build();
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .processDefinitionId(AdvancedStringFilter.Builder.create().$like("process").build())
            .build();
    final var batchOperationInstruction =
        ProcessInstanceMigrationBatchOperationRequest.Builder.create()
            .filter(filter)
            .migrationPlan(migrationPlan)
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest> result =
        mapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationInstruction);

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
    final var mappingInstruction =
        MigrateProcessInstanceMappingInstruction.Builder.create()
            .sourceElementId("")
            .targetElementId("")
            .build();
    final var migrationPlan =
        ProcessInstanceMigrationBatchOperationPlan.Builder.create()
            .targetProcessDefinitionKey("123")
            .mappingInstructions(List.of(mappingInstruction))
            .build();
    final var filter = ProcessInstanceFilter.Builder.create().build();
    final var batchOperationRequest =
        ProcessInstanceMigrationBatchOperationRequest.Builder.create()
            .filter(filter)
            .migrationPlan(migrationPlan)
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest> result =
        mapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationRequest);

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
        ProcessInstanceModificationMoveBatchOperationInstruction.Builder.create()
            .sourceElementId("source1")
            .targetElementId("target1")
            .build();
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .processDefinitionId(AdvancedStringFilter.Builder.create().$like("process").build())
            .build();
    final var modificationRequest =
        ProcessInstanceModificationBatchOperationRequest.Builder.create()
            .filter(filter)
            .moveInstructions(List.of(moveInstruction))
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        mapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

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
              assertThat(instruction.isUseSourceParentKeyAsAncestorScopeKey()).isFalse();
              assertThat(instruction.getVariableInstructions()).isEmpty();
            });
  }

  @Test
  void shouldNotMapProcessInstanceModifyBatchOperationRequestWhenInvalid() {
    // given
    // Use empty targetElementId to trigger "No targetElementId provided." validation
    final var moveInstruction =
        ProcessInstanceModificationMoveBatchOperationInstruction.Builder.create()
            .sourceElementId("source1")
            .targetElementId("")
            .build();
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .processDefinitionId(AdvancedStringFilter.Builder.create().$like("process").build())
            .build();
    final var modificationRequest =
        ProcessInstanceModificationBatchOperationRequest.Builder.create()
            .filter(filter)
            .moveInstructions(List.of(moveInstruction))
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        mapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("No targetElementId provided.");
  }
}
