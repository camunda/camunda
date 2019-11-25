/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.zeebe.operation;

import java.time.OffsetDateTime;

import org.camunda.operate.Metrics;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationState;
import org.camunda.operate.webapp.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public abstract class AbstractOperationHandler implements OperationHandler {

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
  
  protected void recordCommandMetric(final OperationEntity operation) {
    metrics.recordCounts(Metrics.COUNTER_NAME_COMMANDS, 1, Metrics.TAG_KEY_STATUS,operation.getState().name(),Metrics.TAG_KEY_TYPE, operation.getType().name()); 
  }
 
  protected void failOperation(OperationEntity operation, String errorMsg) throws PersistenceException {
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
    recordCommandMetric(operation);
  }

  protected void markAsSucceeded(OperationEntity operation) throws PersistenceException {
    if (operation.getState().equals(OperationState.LOCKED) && operation.getLockOwner().equals(operateProperties.getOperationExecutor().getWorkerId())
      && operation.getType().equals(getType())) {
      operation.setState(OperationState.SENT);
      operation.setLockExpirationTime(null);
      operation.setLockOwner(null);
      batchOperationWriter.updateOperation(operation);
      logger.debug("Operation {} was sent to Zeebe", operation.getId());
    }
    recordCommandMetric(operation);
  }
}
