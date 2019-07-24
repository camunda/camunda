/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import java.time.OffsetDateTime;

import org.camunda.operate.Metrics;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public abstract class AbstractOperationHandler implements OperationHandler {

  // Metric signature
  private static final String COMMANDS = "commands", STATUS = "status", SUCCEEDED="succeeded", FAILED="failed",TYPE="type";

  private static final Logger logger = LoggerFactory.getLogger(AbstractOperationHandler.class);

  @Autowired
  protected BatchOperationWriter batchOperationWriter;
  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  protected Metrics metrics;
  @Override
  public void handle(OperationEntity operation) {
    try {
      handleWithException(operation);
    } catch (PersistenceException ex) {
      try {
        failOperation(operation, String.format("Unable to process operation: %s", ex.getMessage()));
      } catch (PersistenceException e) {
        //
      }
      logger.error("Unable to process operation: " + ex.getMessage(), ex);
    }
  }
  
  protected void recordCommandMetric(final String result,final OperationEntity operation) {
    metrics.recordCounts(COMMANDS, 1, STATUS,result,TYPE, operation.getType().name()); 
  }
  
  protected void recordCommandSucceededMetric(final OperationEntity operation) {
    recordCommandMetric(SUCCEEDED, operation);
  }
  
  protected void recordCommandFailedMetric(final OperationEntity operation) {
    recordCommandMetric(FAILED, operation);
  }

  protected void failOperation(OperationEntity operation, String errorMsg) throws PersistenceException {
    recordCommandFailedMetric(operation);
    if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
      && operation.getType().equals(getType())) {
      operation.setState(OperationState.FAILED);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      operation.setEndDate(OffsetDateTime.now());
      operation.setErrorMessage(StringUtils.trimWhitespace(errorMsg));
      batchOperationWriter.updateOperation(operation);
      logger.debug("Operation {} failed with message: {} ", operation.getId(), operation.getErrorMessage());
    }
  }

  protected void markAsSucceeded(OperationEntity operation) throws PersistenceException {
    recordCommandSucceededMetric(operation);
    if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
      && operation.getType().equals(getType())) {
      operation.setState(OperationState.SENT);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      batchOperationWriter.updateOperation(operation);
      logger.debug("Operation {} was sent to Zeebe", operation.getId());
    }
  }
}
