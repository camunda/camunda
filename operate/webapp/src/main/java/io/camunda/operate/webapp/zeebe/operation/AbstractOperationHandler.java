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
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.zeebe.client.ZeebeClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public abstract class AbstractOperationHandler implements OperationHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractOperationHandler.class);
  private static final List<Status.Code> RETRY_STATUSES =
      Arrays.asList(
          Status.UNAVAILABLE.getCode(),
          Status.RESOURCE_EXHAUSTED.getCode(),
          Status.DEADLINE_EXCEEDED.getCode());

  @Autowired protected ZeebeClient zeebeClient;
  @Autowired protected BatchOperationWriter batchOperationWriter;
  @Autowired protected OperateProperties operateProperties;
  @Autowired protected Metrics metrics;
  @Autowired private OperationsManager operationsManager;

  @Override
  public void handle(OperationEntity operation) {
    try {
      handleWithException(operation);
    } catch (Exception ex) {
      if (isExceptionRetriable(ex)) {
        // leave the operation locked -> when it expires, operation will be retried
        LOGGER.error(
            String.format(
                "Unable to process operation with id %s. Reason: %s. Will be retried.",
                operation.getId(), ex.getMessage()),
            ex);
      } else {
        try {
          failOperation(
              operation, String.format("Unable to process operation: %s", ex.getMessage()));
        } catch (PersistenceException e) {
          // noop
        }
        LOGGER.error(
            String.format(
                "Unable to process operation with id %s. Reason: %s. Will NOT be retried.",
                operation.getId(), ex.getMessage()),
            ex);
      }
    }
  }

  // Needed for tests
  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  private boolean isExceptionRetriable(Exception ex) {
    final StatusRuntimeException cause = extractStatusRuntimeException(ex);
    return cause != null && RETRY_STATUSES.contains(cause.getStatus().getCode());
  }

  private StatusRuntimeException extractStatusRuntimeException(Throwable ex) {
    if (ex.getCause() != null) {
      if (ex.getCause() instanceof StatusRuntimeException) {
        return (StatusRuntimeException) ex.getCause();
      } else {
        return extractStatusRuntimeException(ex.getCause());
      }
    }
    return null;
  }

  protected void recordCommandMetric(final OperationEntity operation) {
    metrics.recordCounts(
        Metrics.COUNTER_NAME_COMMANDS,
        1,
        Metrics.TAG_KEY_STATUS,
        operation.getState().name(),
        Metrics.TAG_KEY_TYPE,
        operation.getType().name());
  }

  protected boolean canForceFailOperation(OperationEntity operation) {
    return false;
  }

  protected void failOperation(OperationEntity operation, String errorMsg)
      throws PersistenceException {
    if (isLocked(operation) || canForceFailOperation(operation)) {
      operation.setState(OperationState.FAILED);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      operation.setErrorMessage(StringUtils.trimWhitespace(errorMsg));
      if (operation.getBatchOperationId() != null) {
        operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
      }
      batchOperationWriter.updateOperation(operation);
      LOGGER.debug(
          "Operation {} failed with message: {} ", operation.getId(), operation.getErrorMessage());
    }
    recordCommandMetric(operation);
  }

  private boolean isLocked(OperationEntity operation) {
    return operation.getState().equals(OperationState.LOCKED)
        && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
        && getTypes().contains(operation.getType());
  }

  protected void markAsSent(OperationEntity operation) throws PersistenceException {
    this.markAsSent(operation, null);
  }

  protected void markAsSent(OperationEntity operation, Long zeebeCommandKey)
      throws PersistenceException {
    if (isLocked(operation)) {
      operation.setState(OperationState.SENT);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      operation.setZeebeCommandKey(zeebeCommandKey);
      batchOperationWriter.updateOperation(operation);
      LOGGER.debug("Operation {} was sent to Zeebe", operation.getId());
    }
    recordCommandMetric(operation);
  }
}
