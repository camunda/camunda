/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class CreateBatchOperationRequestValidatorTest {

  private CreateBatchOperationRequestValidator underTest;

  @BeforeEach
  public void setup() {
    underTest = new CreateBatchOperationRequestValidator();
  }

  @Test
  public void testValidateWithNullQuery() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(null, OperationType.DELETE_PROCESS_INSTANCE);

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(batchOperationRequest))
            .actual();

    assertThat(exception.getMessage()).isEqualTo("List view query must be defined.");
  }

  @Test
  public void testValidateWithNullOperationType() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), null);

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(batchOperationRequest))
            .actual();

    assertThat(exception.getMessage()).isEqualTo("Operation type must be defined.");
  }

  @Test
  public void testAddVariableUnsupported() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.ADD_VARIABLE);

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(batchOperationRequest))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            "For variable update use \"Create operation for one process instance\" endpoint.");
  }

  @Test
  public void testUpdateVariableUnsupported() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.UPDATE_VARIABLE);

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(batchOperationRequest))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            "For variable update use \"Create operation for one process instance\" endpoint.");
  }

  @Test
  public void testValidateMigrateProcessWithNullMigrationPlan() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.MIGRATE_PROCESS_INSTANCE);
    batchOperationRequest.setMigrationPlan(null);

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(batchOperationRequest))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "Migration plan is mandatory for %s operation",
                OperationType.MIGRATE_PROCESS_INSTANCE));
  }

  @Test
  public void testValidateMigrateProcessWithMigrationPlan() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.MIGRATE_PROCESS_INSTANCE);
    final MigrationPlanDto mockMigrationPlan = Mockito.mock(MigrationPlanDto.class);
    batchOperationRequest.setMigrationPlan(mockMigrationPlan);

    underTest.validate(batchOperationRequest);

    verify(mockMigrationPlan, times(1)).validate();
  }

  @Test
  public void testValidateResolveIncident() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.RESOLVE_INCIDENT);

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
  }

  @Test
  public void testValidateCancelProcessInstance() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.CANCEL_PROCESS_INSTANCE);

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
  }

  @Test
  public void testValidateDeleteProcessInstance() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.DELETE_PROCESS_INSTANCE);

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
  }

  @Test
  public void testValidateDeleteDecisionDefinition() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.DELETE_DECISION_DEFINITION);

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
  }

  @Test
  public void testValidateDeleteProcessDefinition() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.DELETE_PROCESS_DEFINITION);

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
  }

  @Test
  public void testValidateWhenModificationsShouldNotBePresent() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), null);
    batchOperationRequest.setModifications(new LinkedList<>());

    // Modifications field is only supported for MODIFY_PROCESS_INSTANCE command, all others
    // should throw an exception if this field is not null
    final var opTypes = new HashSet<>(Set.of(OperationType.values()));
    opTypes.remove(OperationType.MODIFY_PROCESS_INSTANCE);

    for (final OperationType operationType : opTypes) {
      batchOperationRequest.setOperationType(operationType);
      final InvalidRequestException exception =
          assertThatExceptionOfType(InvalidRequestException.class)
              .isThrownBy(() -> underTest.validate(batchOperationRequest))
              .actual();
      assertThat(exception.getMessage())
          .isEqualTo(
              String.format("Modifications field not supported for %s operation", operationType));
    }
  }

  @Test
  public void testValidateModifyProcessInstance() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.MODIFY_PROCESS_INSTANCE);

    batchOperationRequest.setModifications(List.of(new Modification()));

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
    assertThat(batchOperationRequest.getModifications().size()).isEqualTo(1);
  }

  @Test
  public void testValidateModifyProcessInstanceWithTooManyModifications() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.MODIFY_PROCESS_INSTANCE);

    batchOperationRequest.setModifications(
        new LinkedList<>(
            List.of(
                new Modification().setModification(Type.ADD_TOKEN),
                new Modification().setModification(Type.MOVE_TOKEN))));

    assertThatCode(() -> underTest.validate(batchOperationRequest)).doesNotThrowAnyException();
    assertThat(batchOperationRequest.getModifications().size()).isEqualTo(1);
    assertThat(batchOperationRequest.getModifications().get(0).getModification())
        .isEqualTo(Type.ADD_TOKEN);
  }
}
