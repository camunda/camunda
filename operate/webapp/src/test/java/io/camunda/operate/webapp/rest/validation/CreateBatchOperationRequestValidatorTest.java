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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
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
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(batchOperationRequest));

    assertThat(exception.getMessage()).isEqualTo("List view query must be defined.");
  }

  @Test
  public void testValidateWithNullOperationType() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), null);

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(batchOperationRequest));

    assertThat(exception.getMessage()).isEqualTo("Operation type must be defined.");
  }

  @Test
  public void testAddVariableUnsupported() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.ADD_VARIABLE);

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(batchOperationRequest));

    assertThat(exception.getMessage())
        .isEqualTo(
            "For variable update use \"Create operation for one process instance\" endpoint.");
  }

  @Test
  public void testUpdateVariableUnsupported() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.UPDATE_VARIABLE);

    final InvalidRequestException exception =
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(batchOperationRequest));

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
        assertThrows(
            InvalidRequestException.class, () -> underTest.validate(batchOperationRequest));

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

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
  }

  @Test
  public void testValidateCancelProcessInstance() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.CANCEL_PROCESS_INSTANCE);

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
  }

  @Test
  public void testValidateDeleteProcessInstance() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.DELETE_PROCESS_INSTANCE);

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
  }

  @Test
  public void testValidateDeleteDecisionDefinition() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.DELETE_DECISION_DEFINITION);

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
  }

  @Test
  public void testValidateDeleteProcessDefinition() {
    final CreateBatchOperationRequestDto batchOperationRequest =
        new CreateBatchOperationRequestDto(
            new ListViewQueryDto(), OperationType.DELETE_PROCESS_DEFINITION);

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
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
          assertThrows(
              InvalidRequestException.class, () -> underTest.validate(batchOperationRequest));
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

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
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

    assertDoesNotThrow(() -> underTest.validate(batchOperationRequest));
    assertThat(batchOperationRequest.getModifications().size()).isEqualTo(1);
    assertThat(batchOperationRequest.getModifications().get(0).getModification())
        .isEqualTo(Type.ADD_TOKEN);
  }
}
