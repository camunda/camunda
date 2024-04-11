/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.rest.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  public void testValidateWithNullOperationType() {
    final CreateOperationRequestDto operationRequest = new CreateOperationRequestDto(null);

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage()).isEqualTo("Operation type must be defined.");
  }

  @Test
  public void testValidateUpdateVariableWithNullScopeId() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);

    operationRequest.setVariableScopeId(null);
    operationRequest.setVariableName("var");
    operationRequest.setVariableValue("val");

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateUpdateVariableWithNullVariableName() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName(null);
    operationRequest.setVariableValue("val");

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateUpdateVariableWithEmptyVariableName() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName("");
    operationRequest.setVariableValue("val");

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateUpdateVariableWithNullVariableValue() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName("var");
    operationRequest.setVariableValue(null);

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateUpdateVariable() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName("var");
    operationRequest.setVariableValue("val");

    assertDoesNotThrow(() -> underTest.validate(operationRequest, "123"));
  }

  @Test
  public void testValidateAddVariableWithNullScopeId() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.ADD_VARIABLE);

    operationRequest.setVariableScopeId(null);
    operationRequest.setVariableName("var");
    operationRequest.setVariableValue("val");

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateAddVariableWithNullVariableName() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.ADD_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName(null);
    operationRequest.setVariableValue("val");

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateAddVariableWithEmptyVariableName() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.ADD_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName("");
    operationRequest.setVariableValue("val");

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateAddVariableWithNullVariableValue() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.ADD_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName("var");
    operationRequest.setVariableValue(null);

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(operationRequest, "123"));

    assertThat(exception.getMessage())
        .isEqualTo("ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testValidateAddVariable() {
    final CreateOperationRequestDto operationRequest =
        new CreateOperationRequestDto(OperationType.ADD_VARIABLE);

    operationRequest.setVariableScopeId("abc");
    operationRequest.setVariableName("var");
    operationRequest.setVariableValue("val");

    assertDoesNotThrow(() -> underTest.validate(operationRequest, "123"));
  }
}
