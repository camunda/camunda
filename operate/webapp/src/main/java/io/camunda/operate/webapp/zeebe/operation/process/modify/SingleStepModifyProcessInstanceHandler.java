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
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.*;
import static java.util.function.Predicate.not;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.zeebe.operation.AbstractOperationHandler;
import io.camunda.operate.webapp.zeebe.operation.ModifyProcessZeebeWrapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Modify Process Instance Implementation to execute all given modifications in one Zeebe
// 'transaction'
// So for one operation we have only one 'zeebeClient.send().join()'
@Component
public class SingleStepModifyProcessInstanceHandler extends AbstractOperationHandler
    implements ModifyProcessInstanceHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SingleStepModifyProcessInstanceHandler.class);
  @Autowired private ObjectMapper objectMapper;
  @Autowired private OperationsManager operationsManager;
  @Autowired private MoveTokenHandler moveTokenHandler;
  @Autowired private AddTokenHandler addTokenHandler;
  @Autowired private CancelTokenHandler cancelTokenHandler;
  @Autowired private ModifyProcessZeebeWrapper modifyProcessZeebeWrapper;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    // Create request from serialized instructions
    final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest =
        objectMapper.readValue(
            operation.getModifyInstructions(), ModifyProcessInstanceRequestDto.class);
    // Process variable modifications
    modifyVariables(modifyProcessInstanceRequest, operation);

    // Process token (non-variable) modifications
    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep =
        processTokenModifications(modifyProcessInstanceRequest, operation);

    modifyProcessZeebeWrapper.sendModificationsToZeebe(lastStep);
    markAsSent(operation);
    operationsManager.completeOperation(operation, false);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MODIFY_PROCESS_INSTANCE);
  }

  // Needed for tests
  @Override
  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
    modifyProcessZeebeWrapper.setZeebeClient(zeebeClient);
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      processTokenModifications(
          final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest,
          final OperationEntity operation)
          throws PersistenceException {
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep = null;

    final Long processInstanceKey =
        Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
    ModifyProcessInstanceCommandStep1 currentStep =
        modifyProcessZeebeWrapper.newModifyProcessInstanceCommand(processInstanceKey);
    final List<Modification> tokenModifications =
        getTokenModifications(modifyProcessInstanceRequest.getModifications());

    for (final var iter = tokenModifications.iterator(); iter.hasNext(); ) {
      final Modification modification = iter.next();
      ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
      switch (modification.getModification()) {
        case ADD_TOKEN:
          nextStep = addTokenHandler.addToken(currentStep, modification);
          break;
        case CANCEL_TOKEN:
          nextStep = cancelTokenHandler.cancelToken(currentStep, processInstanceKey, modification);
          break;
        case MOVE_TOKEN:
          nextStep = moveTokenHandler.moveToken(currentStep, processInstanceKey, modification);
          break;
        default:
          LOGGER.warn(
              "SingleStepModifyProcessInstanceHandler encountered a modification type that should have been filtered out: {}",
              modification.getModification());
          break;
      }

      // Append 'and' if there is at least one more operation to process
      if (nextStep != null) {
        lastStep = nextStep;
        if (iter.hasNext()) {
          currentStep = nextStep.and();
        }
      }

      // Always update the finished metrics
      operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
    }

    return lastStep;
  }

  private void updateFinishedInBatchOperation(final OperationEntity operation)
      throws PersistenceException {
    operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
  }

  private void modifyVariables(
      final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest,
      final OperationEntity operation)
      throws PersistenceException {
    final Long processInstanceKey =
        Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
    final List<Modification> modifications =
        getVariableModifications(modifyProcessInstanceRequest.getModifications());
    for (final Modification modification : modifications) {
      final Long scopeKey =
          modification.getScopeKey() == null ? processInstanceKey : modification.getScopeKey();
      modifyProcessZeebeWrapper.setVariablesInZeebe(scopeKey, modification.getVariables());
      updateFinishedInBatchOperation(operation);
    }
  }

  private List<Modification> getVariableModifications(final List<Modification> modifications) {
    return modifications.stream().filter(this::isVariableModification).collect(Collectors.toList());
  }

  private List<Modification> getTokenModifications(final List<Modification> modifications) {
    return modifications.stream()
        .filter(not(this::isVariableModification))
        .collect(Collectors.toList());
  }

  private boolean isVariableModification(final Modification modification) {
    return modification.getModification().equals(ADD_VARIABLE)
        || modification.getModification().equals(EDIT_VARIABLE);
  }
}
