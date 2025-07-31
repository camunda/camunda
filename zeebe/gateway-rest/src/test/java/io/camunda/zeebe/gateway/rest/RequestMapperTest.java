/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.AdHocSubProcessActivityServices.AdHocSubProcessActivateActivitiesRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivateActivitiesInstruction;
import io.camunda.zeebe.gateway.protocol.rest.AdHocSubProcessActivateActivityReference;
import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
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

    final var modificationRequest =
        new ProcessInstanceModificationBatchOperationRequest()
            .addMoveInstructionsItem(moveInstruction);

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
            });
  }

  @Test
  void shouldNotMapProcessInstanceModifyBatchOperationRequestWhenInvalid() {
    // given
    final var moveInstruction =
        new ProcessInstanceModificationMoveBatchOperationInstruction().sourceElementId("source1");

    final var modificationRequest =
        new ProcessInstanceModificationBatchOperationRequest()
            .addMoveInstructionsItem(moveInstruction);

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        RequestMapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("No targetElementId provided.");
  }

  @Test
  void shouldMapToAdHocSubProcessActivateActivitiesRequest() {
    // given
    final var element1 = new AdHocSubProcessActivateActivityReference();
    element1.setElementId("activity1");
    element1.setVariables(Map.of("var1", "value1", "count", 42));

    final var element2 = new AdHocSubProcessActivateActivityReference();
    element2.setElementId("activity2");
    element2.setVariables(Map.of("var2", "value2"));

    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of(element1, element2));
    instruction.setCancelRemainingInstances(true);

    final String adHocSubProcessInstanceKey = "123456";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.adHocSubProcessInstanceKey()).isEqualTo(adHocSubProcessInstanceKey);
    assertThat(request.cancelRemainingInstances()).isTrue();
    assertThat(request.elements())
        .hasSize(2)
        .satisfies(
            elements -> {
              assertThat(elements.get(0).elementId()).isEqualTo("activity1");
              assertThat(elements.get(0).variables())
                  .containsExactlyInAnyOrderEntriesOf(Map.of("var1", "value1", "count", 42));
              assertThat(elements.get(1).elementId()).isEqualTo("activity2");
              assertThat(elements.get(1).variables())
                  .containsExactlyInAnyOrderEntriesOf(Map.of("var2", "value2"));
            });
  }

  @Test
  void shouldMapToAdHocSubProcessActivateActivitiesRequestWithCancelRemainingInstancesFalse() {
    // given
    final var element = new AdHocSubProcessActivateActivityReference();
    element.setElementId("activity1");
    element.setVariables(Map.of("key", "value"));

    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of(element));
    instruction.setCancelRemainingInstances(false);

    final String adHocSubProcessInstanceKey = "789012";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.adHocSubProcessInstanceKey()).isEqualTo(adHocSubProcessInstanceKey);
    assertThat(request.cancelRemainingInstances()).isFalse();
    assertThat(request.elements()).hasSize(1);
    assertThat(request.elements().get(0).elementId()).isEqualTo("activity1");
    assertThat(request.elements().get(0).variables())
        .containsExactlyInAnyOrderEntriesOf(Map.of("key", "value"));
  }

  @Test
  void shouldMapToAdHocSubProcessActivateActivitiesRequestWithNullCancelRemainingInstances() {
    // given
    final var element = new AdHocSubProcessActivateActivityReference();
    element.setElementId("activity1");

    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of(element));
    instruction.setCancelRemainingInstances(null);

    final String adHocSubProcessInstanceKey = "345678";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.adHocSubProcessInstanceKey()).isEqualTo(adHocSubProcessInstanceKey);
    assertThat(request.cancelRemainingInstances()).isNull();
    assertThat(request.elements()).hasSize(1);
    assertThat(request.elements().get(0).elementId()).isEqualTo("activity1");
  }

  @Test
  void shouldMapToAdHocSubProcessActivateActivitiesRequestWithEmptyVariables() {
    // given
    final var element = new AdHocSubProcessActivateActivityReference();
    element.setElementId("activity1");
    element.setVariables(Map.of());

    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of(element));
    instruction.setCancelRemainingInstances(true);

    final String adHocSubProcessInstanceKey = "111222";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.elements()).hasSize(1);
    assertThat(request.elements().get(0).elementId()).isEqualTo("activity1");
    assertThat(request.elements().get(0).variables()).isEmpty();
  }

  @Test
  void shouldMapToAdHocSubProcessActivateActivitiesRequestWithNullVariables() {
    // given
    final var element = new AdHocSubProcessActivateActivityReference();
    element.setElementId("activity1");
    element.setVariables(null);

    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of(element));
    instruction.setCancelRemainingInstances(true);

    final String adHocSubProcessInstanceKey = "333444";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.elements()).hasSize(1);
    assertThat(request.elements().get(0).elementId()).isEqualTo("activity1");
    assertThat(request.elements().get(0).variables()).isEmpty();
  }

  @Test
  void shouldReturnProblemDetailForInvalidAdHocSubProcessActivationRequest() {
    // given
    final var element = new AdHocSubProcessActivateActivityReference();
    element.setElementId(null); // Invalid - null element ID

    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of(element));
    instruction.setCancelRemainingInstances(true);

    final String adHocSubProcessInstanceKey = "555666";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400); // Bad Request
    assertThat(problemDetail.getDetail()).contains("elementId");
  }

  @Test
  void shouldMapToAdHocSubProcessActivateActivitiesRequestWithEmptyElementsList() {
    // given
    final var instruction = new AdHocSubProcessActivateActivitiesInstruction();
    instruction.setElements(List.of());
    instruction.setCancelRemainingInstances(true);

    final String adHocSubProcessInstanceKey = "777888";

    // when
    final Either<ProblemDetail, AdHocSubProcessActivateActivitiesRequest> result =
        RequestMapper.toAdHocSubProcessActivateActivitiesRequest(
            adHocSubProcessInstanceKey, instruction);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getTitle()).isEqualTo("INVALID_ARGUMENT");
    assertThat(problemDetail.getStatus()).isEqualTo(400);
  }
}
