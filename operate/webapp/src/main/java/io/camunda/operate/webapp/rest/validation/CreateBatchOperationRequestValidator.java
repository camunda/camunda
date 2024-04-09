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

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.MigrationPlanDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class CreateBatchOperationRequestValidator {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CreateBatchOperationRequestValidator.class);

  public void validate(final CreateBatchOperationRequestDto batchOperationRequest) {
    if (batchOperationRequest.getQuery() == null) {
      throw new InvalidRequestException("List view query must be defined.");
    }

    final OperationType operationType = batchOperationRequest.getOperationType();
    if (operationType == null) {
      throw new InvalidRequestException("Operation type must be defined.");
    }

    if (operationType != OperationType.MODIFY_PROCESS_INSTANCE
        && batchOperationRequest.getModifications() != null) {
      throw new InvalidRequestException(
          String.format("Modifications field not supported for %s operation", operationType));
    }

    switch (operationType) {
      case UPDATE_VARIABLE:
      case ADD_VARIABLE:
        throw new InvalidRequestException(
            "For variable update use \"Create operation for one process instance\" endpoint.");

      case MIGRATE_PROCESS_INSTANCE:
        validateMigrateProcessInstanceType(batchOperationRequest);
        break;

      case MODIFY_PROCESS_INSTANCE:
        validateModifyProcessInstanceType(batchOperationRequest);
        break;
      default:
        break;
    }
  }

  private void validateModifyProcessInstanceType(
      final CreateBatchOperationRequestDto batchOperationRequest) {
    final List<Modification> modifications = batchOperationRequest.getModifications();
    if (CollectionUtils.isEmpty(modifications)) {
      throw new InvalidRequestException("Operation requires a single modification entry.");
    } else if (modifications.size() > 1) {
      LOGGER.warn("Multiple modifications in request, only one will be processed.");
      batchOperationRequest.setModifications(List.of(modifications.get(0)));
    }
  }

  private void validateMigrateProcessInstanceType(
      final CreateBatchOperationRequestDto batchOperationRequest) {
    final MigrationPlanDto migrationPlanDto = batchOperationRequest.getMigrationPlan();
    if (migrationPlanDto == null) {
      throw new InvalidRequestException(
          String.format(
              "Migration plan is mandatory for %s operation",
              OperationType.MIGRATE_PROCESS_INSTANCE));
    }
    migrationPlanDto.validate();
  }
}
