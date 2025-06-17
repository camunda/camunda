/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static io.camunda.webapps.schema.entities.operation.OperationType.ADD_VARIABLE;
import static io.camunda.webapps.schema.entities.operation.OperationType.UPDATE_VARIABLE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import java.util.stream.Stream;
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

  @BeforeEach
  public void setup() {
    underTest = new CreateRequestOperationValidator(mockVariableReader, mockOperationReader);
  }

  @Test
  public void shouldFailValidationWhenOperationTypeIsNull() {
    final var request = new CreateOperationRequestDto(null);

    // when - then
    assertThatThrownBy(() -> underTest.validate(request, "123"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Operation type must be defined.");
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
        .hasMessage("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @ParameterizedTest(name = "should pass for {0} operation with valid scopeId, name, and value")
  @EnumSource(
      value = OperationType.class,
      names = {"ADD_VARIABLE", "UPDATE_VARIABLE"})
  void shouldPassValidationForValidVariableOperation(final OperationType operationType) {
    // given
    final var request = new CreateOperationRequestDto(operationType);
    request.setVariableScopeId("abc");
    request.setVariableName("var");
    request.setVariableValue("val");

    // when - then
    assertDoesNotThrow(() -> underTest.validate(request, "123"));
  }
}
