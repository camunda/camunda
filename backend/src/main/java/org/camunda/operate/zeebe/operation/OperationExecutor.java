/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebe.operation;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.OperationEntity;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@Component
@Configuration
public class OperationExecutor extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(OperationExecutor.class);

  private boolean shutdown = false;

  @Autowired
  private List<OperationHandler> handlers;

  @Autowired
  private BatchOperationWriter batchOperationWriter;

  @Autowired
  private OperateProperties operateProperties;

  private List<ExecutionFinishedListener> listeners = new ArrayList<>();

  public void startExecuting() {
    if (operateProperties.getOperationExecutor().isExecutorEnabled()) {
      start();
    }
  }

  @PreDestroy
  public void shutdown() {
    shutdown = true;
  }

  @Override
  public void run() {
    while (!shutdown) {
      try {
        final Map<String, List<OperationEntity>> operations = executeOneBatch();

        //TODO backoff strategy
        if (operations.size() == 0) {


          notifyExecutionFinishedListeners();

          try {
            Thread.sleep(2000);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }

      } catch (Exception ex) {
        //retry
        logger.error("Something went wrong, while executing operations batch. Will be retried. Underlying exception: ", ex.getCause());

        logger.error(ex.getMessage(), ex);

        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    }
  }

  public Map<String, List<OperationEntity>> executeOneBatch() throws PersistenceException {
    //lock the operations
    final Map<String, List<OperationEntity>> lockedOperations = batchOperationWriter.lockBatch();

    //execute all locked operations
    for (Map.Entry<String, List<OperationEntity>> wiOperations: lockedOperations.entrySet()) {
      for (OperationEntity operation: wiOperations.getValue()) {
        final OperationHandler handler = getOperationHandlers().get(operation.getType());
        if (handler == null) {
          logger.info("Operation {} on worflowInstanceId {} won't be processed, as no suitable handler was found.", operation.getType(), wiOperations.getKey());
        } else {
          handler.handle(operation);
        }
      }
    }
    return lockedOperations;
  }

  @Bean
  public Map<OperationType, OperationHandler> getOperationHandlers() {
    //populate handlers map
    Map<OperationType, OperationHandler> handlerMap = new HashMap<>();
    for (OperationHandler handler: handlers) {
      handlerMap.put(handler.getType(), handler);
    }
    return handlerMap;
  }

  public void registerListener(ExecutionFinishedListener listener) {
    this.listeners.add(listener);
  }

  private void notifyExecutionFinishedListeners() {
    for (ExecutionFinishedListener listener: listeners) {
      listener.onExecutionFinished();
    }
  }
}
