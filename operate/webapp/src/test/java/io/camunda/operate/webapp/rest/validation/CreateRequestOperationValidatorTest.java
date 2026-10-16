/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static io.camunda.webapps.schema.entities.operation.OperationType.ADD_VARIABLE;
import static io.camunda.webapps.schema.entities.operation.OperationType.RESOLVE_INCIDENT;
import static io.camunda.webapps.schema.entities.operation.OperationType.UPDATE_VARIABLE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CreateRequestOperationValidatorTest {
  private CreateRequestOperationValidator underTest;

  @Mock private VariableReader mockVariableReader;

  @Mock private OperationReader mockOperationReader;

  @Mock private IncidentReader mockIncidentReader;

  @Mock private FlowNodeInstanceReader mockFlowNodeInstanceReader;

  @BeforeEach
  public void setup() {
    underTest =
        new CreateRequestOperationValidator(
            mockVariableReader,
            mockOperationReader,
            mockIncidentReader,
            mockFlowNodeInstanceReader);
  }

  @Test
  public void shouldFailValidationWhenOperationTypeIsNull() {
    final var request = new CreateOperationRequestDto(null);

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Operation type must be defined for operation on processInstanceId=123.");
  }

  private static Stream<Arguments> invalidAddOrUpdateVariableOperation() {
    return Stream.of(
        Arguments.of(UPDATE_VARIABLE, null, "name", "val"),
        Arguments.of(UPDATE_VARIABLE, "scope", null, "val"),
        // var name is empty
        Arguments.of(UPDATE_VARIABLE, "scope", "", "val"),
        Arguments.of(UPDATE_VARIABLE, "scope", "name", null),
        Arguments.of(ADD_VARIABLE, null, "name", "val"),
        Arguments.of(ADD_VARIABLE, "scope", null, "val"),
        // var name is empty
        Arguments.of(ADD_VARIABLE, "scope", "", "val"),
        Arguments.of(ADD_VARIABLE, "scope", "name", null));
  }

  @ParameterizedTest(name = "should fail for {0} with scopeId=''{1}'', name=''{2}'', value=''{3}''")
  @MethodSource("invalidAddOrUpdateVariableOperation")
  void shouldFailValidationWhenRequiredFieldsForVariableOperationAreMissing(
      final OperationType type, final String scopeId, final String name, final String value) {
    final var request = new CreateOperationRequestDto(type);
    request.setVariableScopeId(scopeId);
    request.setVariableName(name);
    request.setVariableValue(value);

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "ScopeId, variable name, and variable value must be defined for %s operation on processInstanceId=123.",
            type);
  }

  @Test
  void shouldFailValidationWhenVariableAlreadyExists() {
    // given
    final var request = new CreateOperationRequestDto(ADD_VARIABLE);
    request.setVariableScopeId("456");
    request.setVariableName("name");
    request.setVariableValue("val");
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L)))
        .thenReturn(Set.of(456L));

    // simulate existing variable
    when(mockVariableReader.getVariableByName("123", "456", "name")).thenReturn(new VariableDto());

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Cannot add variable \"name\" in scope \"456\" of processInstanceId=123: "
                + "a variable with this name already exists.");

    // shouldn't query for operations
    verifyNoInteractions(mockOperationReader);
  }

  @ParameterizedTest(
      name = "should fail when ADD_VARIABLE operation with state ''{0}'' exists for same variable")
  @EnumSource(
      value = OperationState.class,
      // we should allow retry after failure)
      names = "FAILED",
      mode = EnumSource.Mode.EXCLUDE)
  void shouldFailValidationWhenOperationWithStateExists(final OperationState opState) {
    // given
    final var request = new CreateOperationRequestDto(ADD_VARIABLE);
    request.setVariableScopeId("456");
    request.setVariableName("name");
    request.setVariableValue("val");
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L)))
        .thenReturn(Set.of(456L));

    // no existing variable
    when(mockVariableReader.getVariableByName("123", "456", "name")).thenReturn(null);

    // simulate pending operation
    final var operation = new OperationDto().setState(opState);
    when(mockOperationReader.getOperations(ADD_VARIABLE, "123", "456", "name"))
        .thenReturn(List.of(operation));

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Cannot add variable \"name\" in scope \"456\" of processInstanceId=123: "
                + "an ADD_VARIABLE operation for this variable already exists.");
  }

  @Test
  void shouldPassValidationWhenFailedAddVariableOperationExists() {
    // given
    final var request = new CreateOperationRequestDto(ADD_VARIABLE);
    request.setVariableScopeId("456");
    request.setVariableName("name");
    request.setVariableValue("val");
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L)))
        .thenReturn(Set.of(456L));

    // no existing variable
    when(mockVariableReader.getVariableByName("123", "456", "name")).thenReturn(null);

    // simulate failed operation exists
    final var failedOperation = new OperationDto().setState(OperationState.FAILED);
    when(mockOperationReader.getOperations(ADD_VARIABLE, "123", "456", "name"))
        .thenReturn(List.of(failedOperation));

    // when - then
    assertThatCode(() -> underTest.validate(request, "123")).doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "should pass for {0} operation with valid scopeId, name, and value")
  @EnumSource(
      value = OperationType.class,
      names = {"ADD_VARIABLE", "UPDATE_VARIABLE"})
  void shouldPassValidationForValidVariableOperation(final OperationType operationType) {
    // given
    final var request = new CreateOperationRequestDto(operationType);
    request.setVariableScopeId("123");
    request.setVariableName("var");
    request.setVariableValue("val");

    // when - then
    assertThatCode(() -> underTest.validate(request, "123")).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectUpdateVariableWhenScopeIsNotANumber() {
    // given: the scope is not a key
    final var request = new CreateOperationRequestDto(UPDATE_VARIABLE);
    request.setVariableScopeId("abc");
    request.setVariableName("name");
    request.setVariableValue("val");

    // when - then: the request is rejected
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Scope abc does not belong to process instance 123.");
  }

  @Test
  void shouldRejectAddVariableWhenScopeIsNotANumber() {
    // given: the scope is not a key
    final var request = new CreateOperationRequestDto(ADD_VARIABLE);
    request.setVariableScopeId("abc");
    request.setVariableName("name");
    request.setVariableValue("val");

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Scope abc does not belong to process instance 123.");
    verifyNoInteractions(mockVariableReader, mockOperationReader);
  }

  @Test
  void shouldRejectNumericScopeIdThatIsNotAFlowNodeInstanceOfTheProcessInstance() {
    // given: 456 is not a flow node instance of process instance 123
    final var request = new CreateOperationRequestDto(UPDATE_VARIABLE);
    request.setVariableScopeId("456");
    request.setVariableName("name");
    request.setVariableValue("val");
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L)))
        .thenReturn(Set.of());

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Scope 456 does not belong to process instance 123.");
  }

  @Test
  void shouldAcceptScopeIdOfProcessInstanceWithLeadingZeros() {
    // given: the scope is the process instance itself, written with a leading zero
    final var request = new CreateOperationRequestDto(UPDATE_VARIABLE);
    request.setVariableScopeId("0123");
    request.setVariableName("name");
    request.setVariableValue("val");

    // when - then
    assertThatCode(() -> underTest.validate(request, "123")).doesNotThrowAnyException();
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  void shouldAcceptScopeIdOfFlowNodeInstanceWithLeadingZeros() {
    // given: the scope is a flow node instance of the process instance, written with a leading zero
    final var request = new CreateOperationRequestDto(UPDATE_VARIABLE);
    request.setVariableScopeId("0456");
    request.setVariableName("name");
    request.setVariableValue("val");
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysOfProcessInstance(123L, Set.of(456L)))
        .thenReturn(Set.of(456L));

    // when - then
    assertThatCode(() -> underTest.validate(request, "123")).doesNotThrowAnyException();
  }

  @Test
  void shouldPassValidationWhenIncidentBelongsToProcessInstance() {
    // given
    final var request = new CreateOperationRequestDto(RESOLVE_INCIDENT).setIncidentId("456");
    when(mockIncidentReader.getIncidentById(456L))
        .thenReturn(new IncidentEntity().setKey(456L).setProcessInstanceKey(123L));

    // when - then
    assertThatCode(() -> underTest.validate(request, "123")).doesNotThrowAnyException();
  }

  @Test
  void shouldPassValidationForResolveIncidentWithoutIncidentId() {
    // given: all incidents of the process instance are resolved
    final var request = new CreateOperationRequestDto(RESOLVE_INCIDENT);

    // when - then
    assertThatCode(() -> underTest.validate(request, "123")).doesNotThrowAnyException();
    verifyNoInteractions(mockIncidentReader);
  }

  @Test
  void shouldFailValidationWhenIncidentBelongsToOtherProcessInstance() {
    // given
    final var request = new CreateOperationRequestDto(RESOLVE_INCIDENT).setIncidentId("456");
    when(mockIncidentReader.getIncidentById(456L))
        .thenReturn(new IncidentEntity().setKey(456L).setProcessInstanceKey(999L));

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No incident 456 found for process instance 123.");
  }

  @Test
  void shouldFailValidationWhenIncidentHasNoProcessInstanceKey() {
    // given
    final var request = new CreateOperationRequestDto(RESOLVE_INCIDENT).setIncidentId("456");
    when(mockIncidentReader.getIncidentById(456L)).thenReturn(new IncidentEntity().setKey(456L));

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No incident 456 found for process instance 123.");
  }

  @Test
  void shouldFailValidationWithSameMessageWhenIncidentDoesNotExist() {
    // given
    final var request = new CreateOperationRequestDto(RESOLVE_INCIDENT).setIncidentId("456");
    when(mockIncidentReader.getIncidentById(456L)).thenThrow(new NotFoundException("not found"));

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No incident 456 found for process instance 123.");
  }

  @Test
  void shouldFailValidationWhenIncidentIdIsNotANumber() {
    // given
    final var request = new CreateOperationRequestDto(RESOLVE_INCIDENT).setIncidentId("abc");

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No incident abc found for process instance 123.");
    verifyNoInteractions(mockIncidentReader);
  }
}
