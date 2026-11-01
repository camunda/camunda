/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ModifyProcessInstanceRequestValidatorTest {
  private ModifyProcessInstanceRequestValidator underTest;

  @Mock private ProcessInstanceReader processInstanceReader;
  @Mock private FlowNodeInstanceReader flowNodeInstanceReader;

  @BeforeEach
  public void setup() {
    underTest =
        new ModifyProcessInstanceRequestValidator(processInstanceReader, flowNodeInstanceReader);
  }

  @Test
  public void testValidateWithMissingProcessInstance() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto().setProcessInstanceKey("123");
    when(processInstanceReader.getProcessInstanceByKey(
            Long.valueOf(request.getProcessInstanceKey())))
        .thenReturn(null);

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "Process instance with key %s does not exist", request.getProcessInstanceKey()));
  }

  @Test
  public void testValidateWithMissingModifications() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto().setProcessInstanceKey("123").setModifications(null);
    when(processInstanceReader.getProcessInstanceByKey(
            Long.valueOf(request.getProcessInstanceKey())))
        .thenReturn(new ProcessInstanceForListViewEntity());

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "No modifications given for process instance with key %s",
                request.getProcessInstanceKey()));
  }

  @Test
  public void testValidateWithNullModificationType() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(List.of(new Modification().setModification(null)));
    when(processInstanceReader.getProcessInstanceByKey(
            Long.valueOf(request.getProcessInstanceKey())))
        .thenReturn(new ProcessInstanceForListViewEntity());

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "Unknown Modification.Type given for process instance with key %s.",
                request.getProcessInstanceKey()));
  }

  @Test
  public void shouldAcceptKeysOfTheProcessInstance() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.ADD_VARIABLE)
                        .setScopeKey(456L)
                        .setVariables(Map.of("a", 1)),
                    new Modification()
                        .setModification(Modification.Type.MOVE_TOKEN)
                        .setFromFlowNodeInstanceKey("456")
                        .setAncestorElementInstanceKey(456L)
                        .setToFlowNodeId("task")));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L)))
        .thenReturn(Set.of(456L));

    assertThatNoException().isThrownBy(() -> underTest.validate(request));
  }

  @Test
  public void shouldRejectVariableScopeOfAnotherProcessInstance() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.EDIT_VARIABLE)
                        .setScopeKey(999L)
                        .setVariables(Map.of("a", 1))));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(999L)))
        .thenReturn(Set.of());

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo("Scope 999 does not belong to process instance 123.");
  }

  @Test
  public void shouldRejectAncestorOfAnotherProcessInstance() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.ADD_TOKEN)
                        .setToFlowNodeId("task")
                        .setAncestorElementInstanceKey(999L)));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(999L)))
        .thenReturn(Set.of());

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo("Element instance 999 does not belong to process instance 123.");
  }

  @Test
  public void shouldRejectCancelTokenSourceOfAnotherProcessInstance() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.CANCEL_TOKEN)
                        .setFromFlowNodeInstanceKey("999")));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(999L)))
        .thenReturn(Set.of());

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo("Element instance 999 does not belong to process instance 123.");
  }

  @Test
  public void shouldRejectForeignKeyInLaterModification() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.ADD_VARIABLE)
                        .setScopeKey(456L)
                        .setVariables(Map.of("a", 1)),
                    new Modification()
                        .setModification(Modification.Type.CANCEL_TOKEN)
                        .setFromFlowNodeInstanceKey("999")));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());
    when(flowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L, 999L)))
        .thenReturn(Set.of(456L));

    final InvalidRequestException exception =
        assertThatExceptionOfType(InvalidRequestException.class)
            .isThrownBy(() -> underTest.validate(request))
            .actual();

    assertThat(exception.getMessage())
        .isEqualTo("Element instance 999 does not belong to process instance 123.");
  }

  @Test
  public void shouldAcceptVariableModificationWithoutScopeKeyWithoutLookingUpFlowNodeInstances() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.ADD_VARIABLE)
                        .setVariables(Map.of("a", 1))));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());

    assertThatNoException().isThrownBy(() -> underTest.validate(request));

    verifyNoInteractions(flowNodeInstanceReader);
  }

  @Test
  public void shouldIgnoreFromFlowNodeInstanceKeyOfAddToken() {
    final ModifyProcessInstanceRequestDto request =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey("123")
            .setModifications(
                List.of(
                    new Modification()
                        .setModification(Modification.Type.ADD_TOKEN)
                        .setToFlowNodeId("task")
                        .setFromFlowNodeInstanceKey("not-a-number")));
    when(processInstanceReader.getProcessInstanceByKey(123L))
        .thenReturn(new ProcessInstanceForListViewEntity());

    assertThatNoException().isThrownBy(() -> underTest.validate(request));
  }
}
