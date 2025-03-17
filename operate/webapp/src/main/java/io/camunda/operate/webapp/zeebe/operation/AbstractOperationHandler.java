/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CommandWithOperationReferenceStep;
import io.camunda.operate.Metrics;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
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

  @Autowired protected CamundaClient camundaClient;
  @Autowired protected BatchOperationWriter batchOperationWriter;
  @Autowired protected OperateProperties operateProperties;
  @Autowired protected Metrics metrics;
  @Autowired private OperationsManager operationsManager;

  @Override
  public void handle(final OperationEntity operation) {
    try {
      handleWithException(operation);
    } catch (final Exception ex) {
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
        } catch (final PersistenceException e) {
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
  @Override
  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  private boolean isExceptionRetriable(final Exception ex) {
    final StatusRuntimeException cause = extractStatusRuntimeException(ex);
    return cause != null && RETRY_STATUSES.contains(cause.getStatus().getCode());
  }

  private StatusRuntimeException extractStatusRuntimeException(final Throwable ex) {
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

  protected boolean canForceFailOperation(final OperationEntity operation) {
    return false;
  }

  protected void failOperation(final OperationEntity operation, final String errorMsg)
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

  private boolean isLocked(final OperationEntity operation) {
    return operation.getState().equals(OperationState.LOCKED)
        && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
        && getTypes().contains(operation.getType());
  }

  protected void markAsSent(final OperationEntity operation) throws PersistenceException {
    markAsSent(operation, null);
  }

  protected void markAsSent(final OperationEntity operation, final Long zeebeCommandKey)
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

  protected static <T extends CommandWithOperationReferenceStep<T>> T withOperationReference(
      final T command, final String id) {
    try {
      final long operationReference = Long.parseLong(id);
      command.operationReference(operationReference);
    } catch (final NumberFormatException e) {
      LOGGER.debug(
          "The operation reference provided is not a number: {}. Ignoring propagating it to zeebe commands.",
          id);
    }
    return command;
  }
}
