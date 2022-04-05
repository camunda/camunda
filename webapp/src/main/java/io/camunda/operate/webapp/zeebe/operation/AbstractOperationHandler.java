/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.zeebe.operation;

import java.util.Arrays;
import java.util.List;
import io.camunda.operate.Metrics;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public abstract class AbstractOperationHandler implements OperationHandler {

  private static final Logger logger = LoggerFactory.getLogger(AbstractOperationHandler.class);
  private static final List<Status.Code> RETRY_STATUSES = Arrays
      .asList(Status.UNAVAILABLE.getCode(), Status.RESOURCE_EXHAUSTED.getCode(), Status.DEADLINE_EXCEEDED.getCode());

  @Autowired
  protected BatchOperationWriter batchOperationWriter;

  @Autowired
  private OperationsManager operationsManager;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  protected Metrics metrics;

  @Override
  public void handle(OperationEntity operation) {
    try {
      handleWithException(operation);
    } catch (Exception ex) {
      if (isExceptionRetriable(ex)) {
        //leave the operation locked -> when it expires, operation will be retried
        logger.error(String.format("Unable to process operation with id %s. Reason: %s. Will be retried.", operation.getId(), ex.getMessage()), ex);
      } else {
        try {
          failOperation(operation, String.format("Unable to process operation: %s", ex.getMessage()));
        } catch (PersistenceException e) {
          //
        }
        logger.error(String.format("Unable to process operation with id %s. Reason: %s. Will NOT be retried.", operation.getId(), ex.getMessage()), ex);
      }
    }
  }

  private boolean isExceptionRetriable(Exception ex) {
    StatusRuntimeException cause = extractStatusRuntimeException(ex);
    return cause != null && RETRY_STATUSES.contains(cause.getStatus().getCode());
  }

  private StatusRuntimeException extractStatusRuntimeException(Throwable ex) {
    if (ex.getCause() != null) {
      if (ex.getCause() instanceof StatusRuntimeException) {
        return (StatusRuntimeException)ex.getCause();
      } else {
        return extractStatusRuntimeException(ex.getCause());
      }
    }
    return null;
  }

  protected void recordCommandMetric(final OperationEntity operation) {
    metrics.recordCounts(Metrics.COUNTER_NAME_COMMANDS, 1, Metrics.TAG_KEY_STATUS,operation.getState().name(),Metrics.TAG_KEY_TYPE, operation.getType().name());
  }

  protected void failOperation(OperationEntity operation, String errorMsg) throws PersistenceException {
    if (isLocked(operation)) {
      operation.setState(OperationState.FAILED);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      operation.setErrorMessage(StringUtils.trimWhitespace(errorMsg));
      if (operation.getBatchOperationId() != null) {
        operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
      }
      batchOperationWriter.updateOperation(operation);
      logger.debug("Operation {} failed with message: {} ", operation.getId(), operation.getErrorMessage());
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

  protected void markAsSent(OperationEntity operation, Long zeebeCommandKey) throws PersistenceException {
    if (isLocked(operation)) {
      operation.setState(OperationState.SENT);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      operation.setZeebeCommandKey(zeebeCommandKey);
      batchOperationWriter.updateOperation(operation);
      logger.debug("Operation {} was sent to Zeebe", operation.getId());
    }
    recordCommandMetric(operation);
  }
}
